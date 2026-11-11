package fr.mediclogger.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;

public final class LogStore extends SQLiteOpenHelper {
    private static final String MEDICINE_SELECT="SELECT m.*,b.remaining AS stock_remaining FROM medicines m LEFT JOIN stock_batches b ON b.id=m.current_batch_id AND b.medicine_id=m.id";
    public LogStore(Context context){this(context,"medic-logger.db");}
    LogStore(Context context,String database){super(context,database,StorageMigration.keyForDatabase(context,database),null,4,0,StorageMigration.KEEP_DATA,null,false);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);db.rawExecSQL("PRAGMA secure_delete=ON");db.rawExecSQL("PRAGMA temp_store=MEMORY");}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, alias TEXT NOT NULL, dose TEXT NOT NULL, color INTEGER NOT NULL, favorite INTEGER NOT NULL DEFAULT 1, archived INTEGER NOT NULL DEFAULT 0, monitor_effects INTEGER NOT NULL DEFAULT 0, current_batch_id INTEGER)");
        createStock(db);
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, medicine_id INTEGER NOT NULL REFERENCES medicines(id), medicine_name TEXT NOT NULL, alias TEXT NOT NULL, dose TEXT NOT NULL, color INTEGER NOT NULL, taken_at INTEGER NOT NULL CHECK(taken_at>0), onset_at INTEGER, ended_at INTEGER, notes TEXT NOT NULL DEFAULT '', zone_id TEXT NOT NULL, monitor_effects INTEGER NOT NULL DEFAULT 1, stock_batch_id INTEGER REFERENCES stock_batches(id), stock_units INTEGER NOT NULL DEFAULT 0 CHECK(stock_units>=0), CHECK(onset_at IS NULL OR onset_at>=taken_at), CHECK(ended_at IS NULL OR ended_at>=COALESCE(onset_at,taken_at)))");
        db.execSQL("CREATE INDEX session_taken ON sessions(taken_at DESC)");db.execSQL("CREATE INDEX session_medicine ON sessions(medicine_id)");
        db.execSQL("ALTER TABLE sessions ADD COLUMN effectiveness_rating INTEGER CHECK(effectiveness_rating BETWEEN 0 AND 10)");
        createReminders(db);
        addCycleColumns(db);
    }
    private void createStock(SQLiteDatabase db){
        db.execSQL("CREATE TABLE stock_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, medicine_id INTEGER NOT NULL REFERENCES medicines(id) ON DELETE CASCADE, initial_quantity INTEGER NOT NULL CHECK(initial_quantity>=0), remaining INTEGER NOT NULL CHECK(remaining>=0), created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX stock_medicine ON stock_batches(medicine_id)");
    }
    private void createReminders(SQLiteDatabase db){
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, medicine_id INTEGER NOT NULL REFERENCES medicines(id) ON DELETE CASCADE, frequency TEXT NOT NULL, times TEXT NOT NULL, week_days INTEGER NOT NULL, month_days TEXT NOT NULL, year_days TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, next_at INTEGER, updated_at INTEGER NOT NULL, last_fired_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX reminder_medicine ON reminders(medicine_id)");
    }
    private void addCycleColumns(SQLiteDatabase db){
        db.execSQL("ALTER TABLE reminders ADD COLUMN cycle_on_days INTEGER NOT NULL DEFAULT 21 CHECK(cycle_on_days BETWEEN 1 AND 3650)");
        db.execSQL("ALTER TABLE reminders ADD COLUMN cycle_off_days INTEGER NOT NULL DEFAULT 7 CHECK(cycle_off_days BETWEEN 0 AND 3650)");
        db.execSQL("ALTER TABLE reminders ADD COLUMN cycle_start TEXT NOT NULL DEFAULT ''");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            // Existing medicines and observations keep their previous effect-monitoring behavior.
            db.execSQL("ALTER TABLE medicines ADD COLUMN monitor_effects INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE medicines ADD COLUMN current_batch_id INTEGER");
            createStock(db);
            db.execSQL("ALTER TABLE sessions ADD COLUMN monitor_effects INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE sessions ADD COLUMN stock_batch_id INTEGER REFERENCES stock_batches(id)");
            db.execSQL("ALTER TABLE sessions ADD COLUMN stock_units INTEGER NOT NULL DEFAULT 0 CHECK(stock_units>=0)");
            createReminders(db);
        }
        if(oldVersion<3)db.execSQL("ALTER TABLE sessions ADD COLUMN effectiveness_rating INTEGER CHECK(effectiveness_rating BETWEEN 0 AND 10)");
        if(oldVersion<4)addCycleColumns(db);
    }
    public synchronized List<Medicine> medicines(boolean includeArchived){
        List<Medicine> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(MEDICINE_SELECT+(includeArchived?"":" WHERE m.archived=0")+" ORDER BY m.archived ASC,m.favorite DESC,m.name COLLATE NOCASE ASC",null)){while(c.moveToNext())out.add(readMedicine(c));}
        return out;
    }
    public synchronized Medicine medicine(long id){try(Cursor c=getReadableDatabase().rawQuery(MEDICINE_SELECT+" WHERE m.id=?",new String[]{""+id})){return c.moveToFirst()?readMedicine(c):null;}}
    public synchronized long saveMedicine(Medicine m){
        m.name=m.name.trim();m.alias=m.alias.trim();m.dose=m.dose.trim();
        if(m.name.isEmpty()||m.name.length()>80)throw new IllegalArgumentException("Enter a medicine name (up to 80 characters).");
        if(m.alias.length()>20||m.dose.length()>80)throw new IllegalArgumentException("Use up to 20 characters for the short name and 80 for the dose.");
        boolean creating=m.id==0;if(creating&&m.stockRemaining!=null)validateQuantity(m.stockRemaining);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();long id=m.id;Long batch=m.stockBatchId;
        try{
            try(Cursor c=db.rawQuery("SELECT id FROM medicines WHERE LOWER(name)=LOWER(?) AND LOWER(dose)=LOWER(?) AND id<>? AND archived=0",new String[]{m.name,m.dose,""+m.id})){if(!m.archived&&c.moveToFirst())throw new IllegalArgumentException("This medicine and dose already exist. Use the saved shortcut.");}
            ContentValues v=new ContentValues();v.put("name",m.name);v.put("alias",m.alias);v.put("dose",m.dose);v.put("color",m.color);v.put("favorite",m.favorite?1:0);v.put("archived",m.archived?1:0);v.put("monitor_effects",m.monitorEffects?1:0);
            if(creating){id=db.insertOrThrow("medicines",null,v);if(m.stockRemaining!=null)batch=insertBatch(db,id,m.stockRemaining);}
            else if(db.update("medicines",v,"id=?",new String[]{""+id})!=1)throw new IllegalArgumentException("This medicine is archived or no longer exists.");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        m.id=id;m.stockBatchId=batch;return id;
    }
    private static void validateQuantity(long quantity){if(quantity<0||quantity>1000000000L)throw new IllegalArgumentException("Enter a quantity from 0 to 1,000,000,000.");}
    private long insertBatch(SQLiteDatabase db,long medicineId,long quantity){
        ContentValues batch=new ContentValues();batch.put("medicine_id",medicineId);batch.put("initial_quantity",quantity);batch.put("remaining",quantity);batch.put("created_at",System.currentTimeMillis());
        long id=db.insertOrThrow("stock_batches",null,batch);ContentValues current=new ContentValues();current.put("current_batch_id",id);db.update("medicines",current,"id=?",new String[]{""+medicineId});return id;
    }
    /** A new pack has its own identity; historical doses never migrate to the new pack. */
    public synchronized void refill(long medicineId,long quantity){
        validateQuantity(quantity);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{Medicine m=medicine(medicineId);if(m==null||m.archived)throw new IllegalArgumentException("This medicine is archived or no longer exists.");insertBatch(db,medicineId,quantity);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized Session take(long medicineId){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();Session s=new Session();long id;
        try{
            Medicine m=medicine(medicineId);if(m==null||m.archived)throw new IllegalArgumentException("This medicine is archived or no longer exists.");
            s.medicineId=m.id;s.medicineName=m.name;s.alias=m.alias;s.dose=m.dose;s.color=m.color;s.monitorEffects=m.monitorEffects;
            s.takenAt=System.currentTimeMillis();s.zoneId=ZoneId.systemDefault().getId();
            if(m.stockBatchId!=null){
                if(m.stockRemaining==null||m.stockRemaining<=0)throw new IllegalArgumentException("This pack is empty. Refill it before logging a dose.");
                ContentValues debit=new ContentValues();debit.put("remaining",m.stockRemaining-1);
                if(db.update("stock_batches",debit,"id=? AND medicine_id=? AND remaining=?",new String[]{""+m.stockBatchId,""+m.id,""+m.stockRemaining})!=1)throw new IllegalArgumentException("Stock changed. Please try again.");
                s.stockBatchId=m.stockBatchId;s.stockUnits=1;
            }
            s.validate(System.currentTimeMillis());id=db.insertOrThrow("sessions",null,sessionValues(s,true));db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        s.id=id;return s;
    }
    private ContentValues sessionValues(Session s,boolean creating){
        ContentValues v=new ContentValues();v.put("medicine_id",s.medicineId);v.put("medicine_name",s.medicineName);v.put("alias",s.alias);v.put("dose",s.dose);v.put("color",s.color);v.put("taken_at",s.takenAt);v.put("onset_at",s.onsetAt);v.put("ended_at",s.endedAt);v.put("notes",s.notes);v.put("zone_id",s.zoneId);v.put("monitor_effects",s.monitorEffects?1:0);
        v.put("effectiveness_rating",s.effectiveness);if(creating){v.put("stock_batch_id",s.stockBatchId);v.put("stock_units",s.stockUnits);}return v;
    }
    public synchronized long saveSession(Session s){
        s.validate(System.currentTimeMillis());if(s.notes.length()>2000)throw new IllegalArgumentException("Use up to 2,000 characters for notes.");
        if(s.id==0){
            // Non-UI imports do not retroactively consume an unrelated current pack.
            s.stockBatchId=null;s.stockUnits=0;s.id=getWritableDatabase().insertOrThrow("sessions",null,sessionValues(s,true));
        }else{
            Session previous=session(s.id);if(previous==null)throw new IllegalArgumentException("This dose no longer exists.");
            if(previous.medicineId!=s.medicineId)throw new IllegalArgumentException("A dose cannot be moved to a different medicine.");
            // Editing a timestamp or notes preserves its original pack and stock debit.
            s.stockBatchId=previous.stockBatchId;s.stockUnits=previous.stockUnits;s.monitorEffects=previous.monitorEffects;s.validate(System.currentTimeMillis());
            if(getWritableDatabase().update("sessions",sessionValues(s,false),"id=?",new String[]{""+s.id})!=1)throw new IllegalArgumentException("This dose no longer exists.");
        }
        return s.id;
    }
    public synchronized void mark(long id,boolean onset){Session s=session(id);if(s==null||!s.active())throw new IllegalArgumentException("This dose is already closed.");if(onset&&s.onsetAt!=null)throw new IllegalArgumentException("The effects have already been recorded.");if(onset)s.onsetAt=System.currentTimeMillis();else s.endedAt=System.currentTimeMillis();saveSession(s);}
    public synchronized void rate(long id,Integer rating){Session s=session(id);if(s==null)throw new IllegalArgumentException("This dose no longer exists.");s.effectiveness=rating;saveSession(s);}
    public synchronized Session session(long id){try(Cursor c=getReadableDatabase().query("sessions",null,"id=?",new String[]{""+id},null,null,null)){return c.moveToFirst()?readSession(c):null;}}
    public synchronized List<Session> sessions(){List<Session> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("sessions",null,null,null,null,null,"taken_at DESC,id DESC")){while(c.moveToNext())out.add(readSession(c));}return out;}
    public synchronized void deleteSession(long id){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            Session s=session(id);
            if(s!=null&&db.delete("sessions","id=?",new String[]{""+id})==1&&s.stockBatchId!=null&&s.stockUnits>0){
                db.execSQL("UPDATE stock_batches SET remaining=remaining+? WHERE id=? AND medicine_id=? AND remaining+?<=initial_quantity AND EXISTS (SELECT 1 FROM medicines WHERE id=? AND current_batch_id=stock_batches.id)",new Object[]{s.stockUnits,s.stockBatchId,s.medicineId,s.stockUnits,s.medicineId});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void deleteMedicine(long id){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM sessions WHERE medicine_id=?",new String[]{""+id})){if(c.moveToFirst()&&c.getLong(0)>0)throw new IllegalArgumentException("This medicine has recorded doses. Keep it archived, or delete its doses from Journal first.");}db.delete("medicines","id=?",new String[]{""+id});db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized List<ReminderRule> reminders(){return remindersFor(null);}
    public synchronized List<ReminderRule> reminders(long medicineId){return remindersFor(medicineId);}
    private List<ReminderRule> remindersFor(Long medicineId){List<ReminderRule> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("reminders",null,medicineId==null?null:"medicine_id=?",medicineId==null?null:new String[]{""+medicineId},null,null,"id ASC")){while(c.moveToNext())out.add(readReminder(c));}return out;}
    public synchronized ReminderRule reminder(long id){try(Cursor c=getReadableDatabase().query("reminders",null,"id=?",new String[]{""+id},null,null,null)){return c.moveToFirst()?readReminder(c):null;}}
    public synchronized long saveReminder(ReminderRule rule){
        rule.validate();Medicine medicine=medicine(rule.medicineId);if(medicine==null||medicine.archived)throw new IllegalArgumentException("This medicine is archived or no longer exists.");
        ReminderRule previous=rule.id==0?null:reminder(rule.id);long revision=Math.max(System.currentTimeMillis(),previous==null?0:previous.revision+1);
        ContentValues v=new ContentValues();v.put("medicine_id",rule.medicineId);v.put("frequency",rule.frequency);v.put("times",rule.times);v.put("week_days",rule.weekDays);v.put("month_days",rule.monthDays);v.put("year_days",rule.yearDays);v.put("cycle_on_days",rule.cycleOnDays);v.put("cycle_off_days",rule.cycleOffDays);v.put("cycle_start",rule.cycleStart);v.put("enabled",rule.enabled?1:0);v.putNull("next_at");v.put("updated_at",revision);
        if(rule.id==0){v.put("last_fired_at",0);rule.id=getWritableDatabase().insertOrThrow("reminders",null,v);}else if(getWritableDatabase().update("reminders",v,"id=?",new String[]{""+rule.id})!=1)throw new IllegalArgumentException("This reminder no longer exists.");
        rule.revision=revision;rule.nextAt=null;return rule.id;
    }
    public synchronized void deleteReminder(long id){getWritableDatabase().delete("reminders","id=?",new String[]{""+id});}
    public synchronized boolean setReminderNext(long id,long revision,Long next){ContentValues v=new ContentValues();v.put("next_at",next);return getWritableDatabase().update("reminders",v,"id=? AND updated_at=?",new String[]{""+id,""+revision})==1;}
    public synchronized boolean claimReminder(long id,long revision,long expected){ContentValues v=new ContentValues();v.put("last_fired_at",expected);v.putNull("next_at");return getWritableDatabase().update("reminders",v,"id=? AND updated_at=? AND enabled=1 AND next_at=? AND last_fired_at<?",new String[]{""+id,""+revision,""+expected,""+expected})==1;}
    private static String str(Cursor c,String key){return c.getString(c.getColumnIndexOrThrow(key));}
    private static long number(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}
    private static Long nullable(Cursor c,String key){int i=c.getColumnIndexOrThrow(key);return c.isNull(i)?null:c.getLong(i);}
    private static Medicine readMedicine(Cursor c){Medicine m=new Medicine();m.id=number(c,"id");m.name=str(c,"name");m.alias=str(c,"alias");m.dose=str(c,"dose");m.color=(int)number(c,"color");m.favorite=number(c,"favorite")==1;m.archived=number(c,"archived")==1;m.monitorEffects=number(c,"monitor_effects")==1;m.stockBatchId=nullable(c,"current_batch_id");m.stockRemaining=nullable(c,"stock_remaining");return m;}
    private static Session readSession(Cursor c){Session s=new Session();s.id=number(c,"id");s.medicineId=number(c,"medicine_id");s.medicineName=str(c,"medicine_name");s.alias=str(c,"alias");s.dose=str(c,"dose");s.color=(int)number(c,"color");s.takenAt=number(c,"taken_at");s.onsetAt=nullable(c,"onset_at");s.endedAt=nullable(c,"ended_at");s.notes=str(c,"notes");s.zoneId=str(c,"zone_id");s.monitorEffects=number(c,"monitor_effects")==1;s.stockBatchId=nullable(c,"stock_batch_id");s.stockUnits=(int)number(c,"stock_units");Long rating=nullable(c,"effectiveness_rating");s.effectiveness=rating==null?null:rating.intValue();return s;}
    private static ReminderRule readReminder(Cursor c){ReminderRule r=new ReminderRule();r.id=number(c,"id");r.medicineId=number(c,"medicine_id");r.frequency=str(c,"frequency");r.times=str(c,"times");r.weekDays=(int)number(c,"week_days");r.monthDays=str(c,"month_days");r.yearDays=str(c,"year_days");r.enabled=number(c,"enabled")==1;r.nextAt=nullable(c,"next_at");r.revision=number(c,"updated_at");r.lastFiredAt=number(c,"last_fired_at");r.cycleOnDays=(int)number(c,"cycle_on_days");r.cycleOffDays=(int)number(c,"cycle_off_days");r.cycleStart=str(c,"cycle_start");return r;}
}
