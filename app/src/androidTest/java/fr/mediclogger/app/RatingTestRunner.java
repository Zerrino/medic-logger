package fr.mediclogger.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;

/** Tests only owned, temporary databases. Never changes the user's records or preferences. */
public final class RatingTestRunner extends Instrumentation {
    private int checks;
    private boolean cleanupFixtures;
    @Override public void onCreate(Bundle args){super.onCreate(args);cleanupFixtures=args!=null&&"true".equals(args.getString("cleanup_review_fixtures"));start();}
    @Override public void onStart(){
        Context context=getTargetContext();String name="review-effectiveness-only.db";Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        if(cleanupFixtures){
            int medicines=0,doses=0;
            try(LogStore db=new LogStore(context)){
                for(Medicine m:db.medicines(true)){
                    boolean owned=(m.name.equals("TEST Review Effects")&&m.alias.equals("TEST-E")&&m.dose.isEmpty())||(m.name.equals("TEST Review Intake")&&m.alias.equals("TEST-I")&&m.dose.isEmpty());
                    if(!owned)continue;
                    for(Session s:db.sessions())if(s.medicineId==m.id){db.deleteSession(s.id);doses++;}
                    for(ReminderRule rule:db.reminders(m.id)){db.deleteReminder(rule.id);ReminderScheduler.dismiss(context,rule.id);}
                    db.deleteMedicine(m.id);medicines++;
                }
                StatusNotifications.update(context);ReminderScheduler.reschedule(context,false);
                android.content.pm.ShortcutManager shortcuts=context.getSystemService(android.content.pm.ShortcutManager.class);
                if(shortcuts!=null){java.util.List<String> ids=new java.util.ArrayList<>();for(android.content.pm.ShortcutInfo shortcut:shortcuts.getDynamicShortcuts()){
                    String label=String.valueOf(shortcut.getLongLabel());if(label.equals("Log dose: TEST Review Effects")||label.equals("Log dose: TEST Review Intake"))ids.add(shortcut.getId());
                }shortcuts.removeDynamicShortcuts(ids);}
                result.putString("stream","Removed "+doses+" owned review doses and "+medicines+" owned review medicines. Other records preserved.\n");code=Activity.RESULT_OK;
            }catch(Throwable failure){result.putString("stream","Cleanup failed: "+failure);}
            finish(code,result);return;
        }
        context.deleteDatabase(name);
        try{
            checks+=ReviewChecks.migrationsOnly(context);
            try(LogStore db=new LogStore(context,name)){
                Medicine monitored=new Medicine();monitored.name="Test effectiveness";monitored.monitorEffects=true;monitored.stockRemaining=3L;db.saveMedicine(monitored);
                Session dose=db.take(monitored.id);expectFailure(()->db.rate(dose.id,5),"Unfinished dose cannot be rated");
                db.mark(dose.id,true);db.mark(dose.id,false);check(db.session(dose.id).effectiveness==null,"Finished dose is initially unrated");
                db.rate(dose.id,0);check(db.session(dose.id).effectiveness==0,"Zero rating persists");db.rate(dose.id,10);check(db.session(dose.id).effectiveness==10,"Ten rating persists");
                expectFailure(()->db.rate(dose.id,-1),"Negative rating rejected");expectFailure(()->db.rate(dose.id,11),"Rating above ten rejected");check(db.session(dose.id).effectiveness==10,"Failed update preserves previous rating");
                db.rate(dose.id,null);check(db.session(dose.id).effectiveness==null,"Rating can be cleared");check(db.medicine(monitored.id).stockRemaining==2,"Rating updates never consume stock");
                Medicine simple=new Medicine();simple.name="Test intake only";db.saveMedicine(simple);Session intake=db.take(simple.id);expectFailure(()->db.rate(intake.id,0),"Intake-only dose cannot be rated");
                for(String column:new String[]{"cycle_on_days","cycle_off_days","cycle_start"})db.getWritableDatabase().execSQL("ALTER TABLE reminders DROP COLUMN "+column);
                db.getWritableDatabase().execSQL("ALTER TABLE sessions DROP COLUMN effectiveness_rating");db.getWritableDatabase().setVersion(2);
            }
            try(LogStore upgraded=new LogStore(context,name)){
                check(upgraded.getReadableDatabase().getVersion()==4&&upgraded.sessions().size()==2,"Encrypted version-two database upgrades without losing records");
                check(upgraded.sessions().stream().allMatch(s->s.effectiveness==null),"Existing intakes remain unrated during upgrade");
                Session completed=upgraded.sessions().stream().filter(s->s.monitorEffects).findFirst().orElseThrow();upgraded.rate(completed.id,7);check(upgraded.session(completed.id).effectiveness==7,"Upgraded dose accepts a new rating");
            }
            result.putString("stream","PASS: "+checks+" migration and effectiveness checks in temporary databases\n");code=Activity.RESULT_OK;
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n"+android.util.Log.getStackTraceString(failure));}
        finally{context.deleteDatabase(name);}
        finish(code,result);
    }
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void expectFailure(Runnable action,String message){boolean failed=false;try{action.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);}
}
