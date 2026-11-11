package fr.mediclogger.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;

/** Owned temporary databases only; does not change any user schedule or settings. */
public final class CycleTestRunner extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Context context=getTargetContext();String name="cycle-review-only.db";Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;context.deleteDatabase(name);
        try{
            checks+=ReviewChecks.migrationsOnly(context);
            long medicineId,sessionId,ruleId;ZoneId zone=ZoneId.systemDefault();
            try(LogStore db=new LogStore(context,name)){
                Medicine medicine=new Medicine();medicine.name="Test cycle persistence";medicine.monitorEffects=true;medicine.stockRemaining=3L;medicineId=db.saveMedicine(medicine);
                Session dose=db.take(medicineId);sessionId=dose.id;db.mark(sessionId,false);db.rate(sessionId,9);
                ReminderRule rule=new ReminderRule();rule.medicineId=medicineId;rule.frequency="cycle";rule.cycleOnDays=21;rule.cycleOffDays=7;rule.cycleStart=LocalDate.now().minusDays(21).toString();rule.times="08:00,20:00";ruleId=db.saveReminder(rule);
                ReminderRule saved=db.reminder(ruleId);check(saved.frequency.equals("cycle")&&saved.cycleOnDays==21&&saved.cycleOffDays==7&&saved.cycleStart.equals(rule.cycleStart),"Cycle configuration is stored in the encrypted database");
                long next=saved.nextAfter(System.currentTimeMillis(),zone);check(Instant.ofEpochMilli(next).atZone(zone).toLocalDate().equals(LocalDate.now().plusDays(7)),"OFF phase schedules the next 21-day block after seven pause days");
                check(db.setReminderNext(ruleId,saved.revision,next)&&db.reminder(ruleId).nextAt==next,"Computed cycle occurrence persists");
                long oldRevision=saved.revision;saved.cycleOnDays=5;saved.cycleOffDays=2;db.saveReminder(saved);
                check(db.reminder(ruleId).nextAt==null&&!db.setReminderNext(ruleId,oldRevision,next),"Editing a cycle invalidates its previous alarm revision");
            }
            try(LogStore db=new LogStore(context,name)){
                ReminderRule r=db.reminder(ruleId);check(r.cycleOnDays==5&&r.cycleOffDays==2&&r.times.equals("08:00,20:00"),"Cycle settings survive a fresh database connection");
                r.frequency="daily";db.saveReminder(r);removeCycleColumns(db);db.getWritableDatabase().setVersion(3);
            }
            try(LogStore db=new LogStore(context,name)){
                ReminderRule r=db.reminder(ruleId);check(db.getReadableDatabase().getVersion()==4&&r.frequency.equals("daily")&&r.times.equals("08:00,20:00"),"Schema-three reminders migrate without changing their frequency or hours");
                check(r.cycleOnDays==21&&r.cycleOffDays==7&&r.cycleStart.isEmpty(),"Existing schedules receive neutral unused cycle fields");
                check(db.session(sessionId).effectiveness==9&&db.medicine(medicineId).stockRemaining==2,"Cycle migration preserves ratings and remaining stock");
                removeCycleColumns(db);db.getWritableDatabase().execSQL("ALTER TABLE sessions DROP COLUMN effectiveness_rating");db.getWritableDatabase().setVersion(2);
            }
            try(LogStore db=new LogStore(context,name)){
                check(db.getReadableDatabase().getVersion()==4&&db.reminder(ruleId).frequency.equals("daily"),"Schema-two database upgrades through both feature migrations");
                check(db.session(sessionId).effectiveness==null&&db.medicine(medicineId).stockRemaining==2,"Older records remain unrated and keep their inventory");
                ReminderRule r=db.reminder(ruleId);r.frequency="cycle";r.cycleStart="2026-01-01";r.cycleOnDays=0;expectFailure(()->db.saveReminder(r),"Invalid zero-day ON block cannot be saved");
                check(db.reminder(ruleId).frequency.equals("daily"),"Rejected cycle edit leaves the previous schedule intact");
            }
            result.putString("stream","PASS: "+checks+" cycle persistence and legacy migration checks\n");code=Activity.RESULT_OK;
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n"+android.util.Log.getStackTraceString(failure));}
        finally{context.deleteDatabase(name);}
        finish(code,result);
    }
    private static void removeCycleColumns(LogStore db){for(String column:new String[]{"cycle_on_days","cycle_off_days","cycle_start"})db.getWritableDatabase().execSQL("ALTER TABLE reminders DROP COLUMN "+column);}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void expectFailure(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);}
}
