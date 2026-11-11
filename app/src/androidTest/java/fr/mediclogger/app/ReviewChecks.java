package fr.mediclogger.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;
import fr.mediclogger.app.Models.ExportData;

/** Regression checks in the separate QA package, including the actual native crypto library. */
final class ReviewChecks {
    private static int checks;
    static int migrationsOnly(Context c)throws Exception{checks=0;migrate(c,false);migrate(c,true);return checks;}
    static int run(Context c)throws Exception{
        if(!c.getPackageName().equals("fr.mediclogger.app.qa"))throw new SecurityException("QA package required");
        migrate(c,false);migrate(c,true);stock(c);crypto(c);preferences(c);alertProfiles(c);reminders(c);return checks;
    }
    private static void migrate(Context c,boolean wal)throws Exception{
        String name="review-legacy-"+wal+".db";c.deleteDatabase(name);File path=c.getDatabasePath(name);
        try{
            long taken=System.currentTimeMillis()-120000;
            try(android.database.sqlite.SQLiteDatabase plain=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null)){
                if(wal)plain.enableWriteAheadLogging();
                plain.execSQL("CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,alias TEXT NOT NULL,dose TEXT NOT NULL,color INTEGER NOT NULL,favorite INTEGER NOT NULL DEFAULT 1,archived INTEGER NOT NULL DEFAULT 0)");
                plain.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT,medicine_id INTEGER NOT NULL REFERENCES medicines(id),medicine_name TEXT NOT NULL,alias TEXT NOT NULL,dose TEXT NOT NULL,color INTEGER NOT NULL,taken_at INTEGER NOT NULL,onset_at INTEGER,ended_at INTEGER,notes TEXT NOT NULL DEFAULT '',zone_id TEXT NOT NULL)");
                plain.execSQL("CREATE INDEX session_taken ON sessions(taken_at DESC)");plain.execSQL("CREATE INDEX session_medicine ON sessions(medicine_id)");
                plain.execSQL("INSERT INTO medicines VALUES(7,'Legacy secret médicament 中文','LG','10 mg',12345,1,0)");
                plain.execSQL("INSERT INTO medicines VALUES(9,'Archived legacy','AR','',12345,0,1)");
                plain.execSQL("INSERT INTO sessions VALUES(14,7,'Original snapshot','LG','10 mg',12345,?,?,?,'Legacy secret note','Europe/Lisbon')",new Object[]{taken,taken+10000,taken+20000});plain.setVersion(1);
            }
            check(new String(Files.readAllBytes(path.toPath()),StandardCharsets.ISO_8859_1).contains("Legacy secret"),"Legacy fixture starts in plaintext; WAL="+wal);
            try(LogStore db=new LogStore(c,name)){
                check(db.medicines(true).size()==2&&db.sessions().size()==1,"Migration preserves records; WAL="+wal);
                check(db.medicine(7).name.equals("Legacy secret médicament 中文")&&db.medicine(9).archived,"Migration preserves Unicode and archive state");
                check(db.medicine(7).monitorEffects&&db.session(14).monitorEffects,"Legacy records retain effect monitoring");
                check(db.session(14).status().equals("completed")&&db.session(14).medicineName.equals("Original snapshot"),"Migration preserves observations and snapshots");
                check(db.session(14).stockBatchId==null&&db.session(14).stockUnits==0,"Legacy intake does not consume a new refill");
                check(db.getReadableDatabase().getVersion()==4&&db.reminders().isEmpty(),"Schema upgrade is applied after encryption");
            }
            byte[] encrypted=Files.readAllBytes(path.toPath());check(!new String(encrypted,StandardCharsets.ISO_8859_1).contains("Legacy secret"),"Database pages contain no plaintext fixture");
            check(!new String(encrypted,0,16,StandardCharsets.US_ASCII).startsWith("SQLite format"),"Database header is encrypted");
            boolean wrongKey=false;
            try(net.zetetic.database.sqlcipher.SQLiteDatabase wrong=net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(path.getPath(),"wrong-key".getBytes(StandardCharsets.UTF_8),null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,StorageMigration.KEEP_DATA,null)){
                try(Cursor cursor=wrong.rawQuery("SELECT COUNT(*) FROM medicines",null)){cursor.moveToFirst();}
            }catch(android.database.sqlite.SQLiteException expected){wrongKey=true;}
            check(wrongKey&&path.exists(),"Wrong key is rejected without deleting the database");
            try(LogStore db=new LogStore(c,name)){check(db.session(14).notes.equals("Legacy secret note"),"Encrypted database reopens after rejected key");}
        }finally{c.deleteDatabase(name);}
    }
    private static void stock(Context c)throws Exception{
        String name="review-stock.db";c.deleteDatabase(name);
        try(LogStore db=new LogStore(c,name)){
            Medicine a=new Medicine();a.name="QA pack medicine";a.stockRemaining=2L;db.saveMedicine(a);
            check(!db.medicine(a.id).monitorEffects,"Effect monitoring is disabled by default");
            Session old=db.take(a.id),other=db.take(a.id);long originalBatch=old.stockBatchId;
            check(!old.active()&&old.status().equals("taken")&&old.onsetAt==null&&old.endedAt==null,"Intake-only doses have no effect phase");
            check(db.medicine(a.id).stockRemaining==0,"Each intake consumes exactly one unit");
            expectFailure(()->db.take(a.id),"Empty stock refuses intake");check(db.sessions().size()==2&&db.medicine(a.id).stockRemaining==0,"Rejected intake changes neither stock nor history");
            expectFailure(()->db.mark(old.id,true),"Intake-only dose rejects onset");
            db.deleteSession(other.id);check(db.medicine(a.id).stockRemaining==1,"Deleting a current-pack intake refunds one unit");
            db.deleteSession(other.id);check(db.medicine(a.id).stockRemaining==1,"Repeated deletion cannot refund twice");
            a.alias="PACK";db.saveMedicine(a);check(db.medicine(a.id).stockRemaining==1,"Saving stale medicine object does not overwrite stock");
            db.refill(a.id,5);long refill=db.medicine(a.id).stockBatchId;check(refill!=originalBatch&&db.medicine(a.id).stockRemaining==5,"Refill starts a distinct pack");
            old.takenAt-=86400000;db.saveSession(old);check(db.session(old.id).stockBatchId==originalBatch,"Edited intake stays tied to its original pack");
            db.deleteSession(old.id);check(db.medicine(a.id).stockRemaining==5,"Deleting an old-pack intake does not change the new pack");
            Session simple=db.take(a.id);a.monitorEffects=true;db.saveMedicine(a);Session monitored=db.take(a.id);
            check(!db.session(simple.id).monitorEffects&&monitored.monitorEffects&&monitored.active(),"Monitoring changes apply only to new intakes");
            db.mark(monitored.id,true);db.mark(monitored.id,false);check(db.medicine(a.id).stockRemaining==3,"Effect actions never consume more stock");
            db.deleteSession(monitored.id);check(db.medicine(a.id).stockRemaining==4,"Deleting monitored intake refunds its own unit");
            Medicine b=new Medicine();b.name="QA untracked stock";db.saveMedicine(b);Session untracked=db.take(b.id);check(untracked.stockBatchId==null&&db.medicine(b.id).stockRemaining==null,"Optional stock can remain untracked");
            ExportData selected=ExportData.forMedicines(db.sessions(),LocalDate.now().minusDays(2),LocalDate.now().plusDays(1),ZoneId.systemDefault(),Set.of(a.id,b.id),System.currentTimeMillis());
            check(selected.sessions.size()==2&&selected.events.size()==2,"Multiple selected medicines export their intake-only events");
            check(ExportData.forMedicines(db.sessions(),selected.from,selected.to,selected.zone,Set.of(),0).events.isEmpty(),"Empty export selection includes no medicines");
            db.refill(a.id,1);CyclicBarrier barrier=new CyclicBarrier(2);var workers=Executors.newFixedThreadPool(2);
            try{
                Callable<Boolean> intake=()->{try(LogStore concurrent=new LogStore(c,name)){barrier.await();try{concurrent.take(a.id);return true;}catch(IllegalArgumentException empty){return false;}}};
                var one=workers.submit(intake);var two=workers.submit(intake);int successes=(one.get()?1:0)+(two.get()?1:0);
                check(successes==1&&db.medicine(a.id).stockRemaining==0,"Concurrent intakes cannot overspend the last unit");
            }finally{workers.shutdownNow();}
        }finally{c.deleteDatabase(name);}
    }
    private static void crypto(Context c)throws Exception{
        byte[] plain="QA secret notes / médicament 中文".getBytes(StandardCharsets.UTF_8),sealed=CryptoBox.seal(plain,"review");
        check(Arrays.equals(plain,CryptoBox.open(sealed,"review")),"AES-GCM authenticates and decrypts data");
        check(!Arrays.equals(sealed,CryptoBox.seal(plain,"review")),"Each encryption uses a fresh nonce");
        byte[] changed=sealed.clone();changed[changed.length-1]^=1;expectIo(()->CryptoBox.open(changed,"review"),"Modified ciphertext is rejected");
        expectIo(()->CryptoBox.open(sealed,"other-purpose"),"Ciphertext is bound to its storage purpose");
        File pending=new File(c.getNoBackupFilesDir(),"review-export.enc");
        try{
            try(OutputStream out=CryptoBox.exportOutput(pending)){out.write(plain);}
            check(CryptoBox.encrypted(pending)&&!new String(Files.readAllBytes(pending.toPath()),StandardCharsets.ISO_8859_1).contains("QA secret"),"Pending export stays encrypted on disk");
            try(InputStream in=CryptoBox.exportInput(pending)){check(Arrays.equals(plain,in.readAllBytes()),"Export becomes plaintext only while copying to its selected destination");}
            byte[] large=new byte[140000];for(int n=0;n<large.length;n++)large[n]=(byte)(n%251);
            try(OutputStream out=CryptoBox.exportOutput(pending)){for(int offset=0;offset<large.length;offset+=7000)out.write(large,offset,Math.min(7000,large.length-offset));}
            try(InputStream in=CryptoBox.exportInput(pending)){check(Arrays.equals(large,in.readAllBytes()),"Large encrypted export survives multiple stream chunks");}
            byte[] tampered=Files.readAllBytes(pending.toPath());tampered[tampered.length-1]^=1;Files.write(pending.toPath(),tampered);
            expectIo(()->{try(InputStream in=CryptoBox.exportInput(pending)){in.readAllBytes();}},"Modified export ciphertext is rejected");
        }finally{pending.delete();}
    }
    private static void preferences(Context c)throws Exception{
        String suffix=""+System.nanoTime();String name="review-prefs-"+suffix,legacy="review-legacy-prefs-"+suffix;
        c.getSharedPreferences(legacy,Context.MODE_PRIVATE).edit().putString("secret","QA secret preferences").putLong("large",1234567890123L).putBoolean("enabled",true).putInt("stock",23).putFloat("fraction",1.5f).putStringSet("set",Set.of("one","two")).commit();
        SharedPreferences prefs=SecurePreferences.get(c,name,legacy);
        check(prefs.getString("secret","").equals("QA secret preferences")&&prefs.getLong("large",0)==1234567890123L,"Legacy preferences migrate without changing their types");
        check(prefs.getBoolean("enabled",false)&&prefs.getInt("stock",0)==23&&prefs.getFloat("fraction",0)==1.5f,"Boolean, int and float settings survive migration");
        Set<String> copy=prefs.getStringSet("set",Set.of());copy.clear();check(prefs.getStringSet("set",Set.of()).size()==2,"Preference sets cannot be modified through a read result");
        File encoded=new File(c.getNoBackupFilesDir(),name+".enc"),xml=new File(c.getApplicationInfo().dataDir,"shared_prefs/"+legacy+".xml");
        check(!xml.exists()&&!new String(Files.readAllBytes(encoded.toPath()),StandardCharsets.ISO_8859_1).contains("QA secret"),"Encrypted preference replacement removes legacy plaintext XML");
        prefs.edit().remove("secret").putString("new","persisted").commit();
        java.lang.reflect.Field cache=SecurePreferences.class.getDeclaredField("INSTANCES");cache.setAccessible(true);
        synchronized(SecurePreferences.class){((java.util.Map<?,?>)cache.get(null)).remove(c.getPackageName()+":"+name);}
        SharedPreferences reopened=SecurePreferences.get(c,name,legacy);check(!reopened.contains("secret")&&reopened.getString("new","").equals("persisted"),"Encrypted preferences persist edits across a fresh instance");
        reopened.edit().clear().commit();check(reopened.getAll().isEmpty(),"Preference clear is durable");
    }
    private static void reminders(Context c)throws Exception{
        Medicine a=new Medicine(),b=new Medicine();a.name="QA reminder intake";b.name="QA reminder monitored";b.monitorEffects=true;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        try(LogStore db=new LogStore(c)){
            db.saveMedicine(a);db.saveMedicine(b);ReminderRule first=new ReminderRule(),second=new ReminderRule();first.medicineId=a.id;second.medicineId=b.id;
            try{
                db.saveReminder(first);db.saveReminder(second);ReminderScheduler.reschedule(c,false);
                check(db.reminder(first.id).nextAt>System.currentTimeMillis(),"Reminder is persisted with its next occurrence");
                long stale=first.revision;db.saveReminder(first);check(!db.setReminderNext(first.id,stale,1L),"Stale reminder revision cannot overwrite a newer schedule");
                long due=System.currentTimeMillis()-1000;db.setReminderNext(first.id,first.revision,due);db.setReminderNext(second.id,second.revision,due);
                ReminderScheduler.deliver(c);
                if(StatusNotifications.allowed(c)){
                    await(()->childCount(manager)==2&&summary(manager),"Simultaneous reminder notifications and summary appear");
                    Notification n=find(manager,"reminder-"+first.id,1);
                    check(n.getChannelId().equals(ReminderScheduler.CHANNEL)&&n.category.equals(Notification.CATEGORY_REMINDER),"Reminder uses its own native channel and category");
                    check(n.visibility==Notification.VISIBILITY_PRIVATE&&n.publicVersion!=null&&"Medic Logger".equals(n.publicVersion.extras.getString(Notification.EXTRA_TITLE)),"Lock-screen public version contains no medication name");
                    check(n.actions.length==1&&!n.actions[0].actionIntent.equals(n.contentIntent),"Reminder body and intake confirmation have distinct destinations");
                }
                check(!db.claimReminder(first.id,first.revision,due)&&db.reminder(first.id).lastFiredAt==due,"Reminder delivery is idempotent");
                ReminderScheduler.deliver(c);check(db.reminder(first.id).nextAt>due,"Delivery schedules the next occurrence");
                Session simple=db.take(a.id);check(!StatusNotifications.active(db).stream().anyMatch(s->s.id==simple.id),"Intake-only medicine never appears in ongoing effects");db.deleteSession(simple.id);
                ReminderScheduler.dismissMedicine(c,a.id);
                if(StatusNotifications.allowed(c))await(()->childCount(manager)==1,"Taking a medicine preserves the other medicine's reminder");
                b.archived=true;db.saveMedicine(b);ReminderScheduler.reschedule(c,false);
                check(db.reminder(second.id).nextAt==null,"Archiving stops future reminders");
                if(StatusNotifications.allowed(c))await(()->childCount(manager)==0,"Archiving clears the visible reminder");
                first.enabled=false;db.saveReminder(first);ReminderScheduler.reschedule(c,false);check(db.reminder(first.id).nextAt==null,"Disabled rules are not scheduled");
                first.enabled=true;db.saveReminder(first);AppPrefs.get(c).edit().putBoolean("reminders_enabled",false).commit();ReminderScheduler.reschedule(c,false);
                check(db.reminder(first.id).enabled&&db.reminder(first.id).nextAt==null,"Global reminder toggle stops alarms without deleting the medicine schedule");
                AppPrefs.get(c).edit().putBoolean("reminders_enabled",true).commit();ReminderScheduler.reschedule(c,false);check(db.reminder(first.id).nextAt>System.currentTimeMillis(),"Re-enabling reminders restores the next future occurrence");
                first.times=java.time.LocalTime.now().plusSeconds(5).withNano(0).toString();db.saveReminder(first);ReminderScheduler.reschedule(c,false);
                long expected=db.reminder(first.id).nextAt;check(expected>System.currentTimeMillis()&&expected-System.currentTimeMillis()<=5000,"Exact native alarm is registered for the upcoming occurrence");
                boolean fired=false;for(int n=0;n<400;n++){if(db.reminder(first.id).lastFiredAt==expected){fired=true;break;}SystemClock.sleep(25);}
                check(fired,"Android AlarmManager actually invokes the reminder receiver in the background");
                if(StatusNotifications.allowed(c))await(()->childCount(manager)==1,"Native alarm delivery posts the expected medication reminder");
            }finally{db.deleteReminder(first.id);db.deleteReminder(second.id);ReminderScheduler.dismiss(c,first.id);ReminderScheduler.dismiss(c,second.id);db.deleteMedicine(a.id);db.deleteMedicine(b.id);ReminderScheduler.reschedule(c,false);}
        }
    }
    private static void alertProfiles(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);java.util.Set<String> ids=new java.util.HashSet<>();
        for(int mode=1;mode<=4;mode++){
            AppPrefs.get(c).edit().putInt("reminder_alert_mode",mode).commit();String id=ReminderAlerts.ensureChannel(c);ids.add(id);android.app.NotificationChannel channel=manager.getNotificationChannel(id);
            boolean sound=mode==ReminderAlerts.SOUND||mode==ReminderAlerts.SOUND_AND_VIBRATION,vibrate=mode==ReminderAlerts.VIBRATION||mode==ReminderAlerts.SOUND_AND_VIBRATION;
            check((channel.getSound()!=null)==sound&&channel.shouldVibrate()==vibrate,"Android alert profile honors independent sound/vibration selection: "+mode);
            if(mode==ReminderAlerts.SILENT)check(channel.getImportance()==NotificationManager.IMPORTANCE_LOW,"Silent reminder channel does not request an audible alert");
        }
        check(ids.size()==4,"Alert profiles have independent immutable channels");
        AppPrefs.get(c).edit().putInt("reminder_alert_mode",ReminderAlerts.SOUND).putString("reminder_sound","content://media/internal/audio/media/1").commit();
        check(!ids.contains(ReminderAlerts.channelId(c)),"Choosing another ringtone selects a channel with the new sound");
        AppPrefs.get(c).edit().remove("reminder_alert_mode").remove("reminder_sound").commit();
        check(ReminderAlerts.channelId(c).equals(ReminderAlerts.LEGACY_CHANNEL),"Default mode preserves the existing Android notification channel");
    }
    private static int childCount(NotificationManager manager){int n=0;for(StatusBarNotification value:manager.getActiveNotifications())if(value.getId()==1&&value.getTag()!=null&&value.getTag().startsWith("reminder-"))n++;return n;}
    private static boolean summary(NotificationManager manager){return find(manager,null,8001)!=null;}
    private static Notification find(NotificationManager manager,String tag,int id){for(StatusBarNotification value:manager.getActiveNotifications())if(value.getId()==id&&java.util.Objects.equals(value.getTag(),tag))return value.getNotification();return null;}
    private static void await(java.util.function.BooleanSupplier condition,String message){for(int i=0;i<100;i++){if(condition.getAsBoolean()){check(true,message);return;}SystemClock.sleep(25);}check(false,message);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void expectFailure(Runnable action,String message){boolean failed=false;try{action.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);}
    private interface IoAction{void run()throws Exception;}
    private static void expectIo(IoAction action,String message)throws Exception{boolean failed=false;try{action.run();}catch(java.io.IOException expected){failed=true;}check(failed,message);}
}
