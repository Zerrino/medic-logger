package fr.mediclogger.app;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import fr.mediclogger.app.Models.ExportData;
import fr.mediclogger.app.Models.Session;

/** Dependency-free JVM tests. Run scripts/test-core.sh. */
public final class CoreTests {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Session s=session(1,1,"2026-10-24T22:55:00Z");
        s.onsetAt=at("2026-10-24T23:30:00Z"); s.endedAt=at("2026-10-25T02:00:00Z");
        s.medicineName="Example, \"medicine\" é 💊"; s.alias="=NOT_A_FORMULA()"; s.dose="10 mg";s.notes="Line 1\nLine 2, with \"quotes\" & <XML>\t";
        s.zoneId="Europe/Lisbon";
        s.effectiveness=8;
        Session next=session(2,2,"2026-10-26T00:00:00Z");
        ExportData fall=new ExportData(List.of(s,next),LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),ZoneId.of("Europe/Lisbon"),null,System.currentTimeMillis());
        check(fall.events.size()==2,"Fall DST date includes both effect events and excludes doses on surrounding days");
        check(fall.events.get(0).type.equals("effects_started"),"Dose before range keeps its in-range effect onset");
        check(fall.sessions.size()==1 && fall.sessions.get(0).takenAt==s.takenAt,"Full dose context is preserved");
        check(new ExportData(List.of(s),fall.from,fall.to,fall.zone,2L,0).events.isEmpty(),"Medicine filter excludes other medicines");
        Session spring=session(3,1,"2026-03-29T00:00:00Z");spring.onsetAt=at("2026-03-29T22:59:59.999Z");spring.endedAt=at("2026-03-29T23:00:00Z");
        ExportData springRange=new ExportData(List.of(spring),LocalDate.parse("2026-03-29"),LocalDate.parse("2026-03-29"),fall.zone,null,0);
        check(springRange.events.size()==2,"Spring DST day uses actual next midnight, not 24 hours");
        Session boundary=session(4,1,"2026-01-01T00:00:00Z");boundary.onsetAt=at("2026-01-01T23:59:59.999Z");boundary.endedAt=at("2026-01-02T00:00:00Z");
        check(new ExportData(List.of(boundary),LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-01"),ZoneId.of("UTC"),null,0).events.size()==2,"Inclusive start and exclusive next midnight");
        expectFailure(()->new ExportData(List.of(),fall.to,fall.from.minusDays(1),fall.zone,null,0),"Invalid date range rejected");
        Session invalid=session(5,1,"2026-01-01T00:00:00Z");invalid.onsetAt=invalid.takenAt-1;
        expectFailure(()->invalid.validate(System.currentTimeMillis()),"Onset before dose rejected");
        invalid.onsetAt=invalid.takenAt; invalid.endedAt=invalid.takenAt-1;
        expectFailure(()->invalid.validate(System.currentTimeMillis()),"End before onset rejected");
        invalid.endedAt=System.currentTimeMillis()+60000;
        expectFailure(()->invalid.validate(System.currentTimeMillis()),"Future events rejected");
        Session noEffect=session(6,1,"2026-01-01T00:00:00Z");noEffect.endedAt=noEffect.takenAt+1000;
        check(noEffect.status().equals("closed_without_effect"),"No-effect closure is distinct from effect ending");
        check(Models.elapsed(3600000+120000).equals("1 h 2 min"),"Duration formatting");
        Session rated=session(20,1,"2026-01-01T00:00:00Z");rated.effectiveness=0;
        expectFailure(()->rated.validate(System.currentTimeMillis()),"An active dose cannot be rated");rated.endedAt=rated.takenAt+1000;rated.validate(System.currentTimeMillis());check(rated.effectiveness==0,"Zero is a valid rating, distinct from an omitted rating");
        rated.effectiveness=10;rated.validate(System.currentTimeMillis());check(rated.effectiveness==10,"Ten is a valid rating");rated.effectiveness=-1;expectFailure(()->rated.validate(System.currentTimeMillis()),"Negative rating rejected");rated.effectiveness=11;expectFailure(()->rated.validate(System.currentTimeMillis()),"Rating over ten rejected");rated.effectiveness=0;rated.monitorEffects=false;expectFailure(()->rated.validate(System.currentTimeMillis()),"An intake-only medicine cannot receive an effect rating");
        Session intake=session(8,3,"2026-10-25T12:00:00Z");intake.monitorEffects=false;
        check(intake.status().equals("taken")&&!intake.active(),"Intake-only doses are immediately recorded without ongoing effects");
        intake.onsetAt=intake.takenAt+1;expectFailure(()->intake.validate(System.currentTimeMillis()),"Intake-only records reject effect observations");intake.onsetAt=null;
        List<Session> mixed=List.of(s,next,intake);
        check(ExportData.forMedicines(mixed,fall.from,fall.to,fall.zone,java.util.Set.of(1L,3L),0).events.size()==3,"Multiple selected medicines include both monitored and intake-only events");
        check(ExportData.forMedicines(mixed,fall.from,fall.to,fall.zone,java.util.Set.of(2L),0).events.isEmpty(),"A selected medicine outside the date range contributes nothing");
        check(ExportData.forMedicines(mixed,fall.from,fall.to,fall.zone,java.util.Set.of(),0).events.isEmpty(),"An empty medicine set never becomes all medicines");
        check(ExportData.forMedicines(mixed,fall.from,fall.to,fall.zone,null,0).events.size()==3,"Null selection retains all medicines");
        reminderChecks();
        cycleChecks();
        File output=new File(args.length==0?"artifacts/core-tests":args[0]);output.mkdirs();
        for(ExportWriter.Format f:ExportWriter.Format.values()) {
            try(FileOutputStream out=new FileOutputStream(new File(output,"example."+f.extension))) {ExportWriter.write(out,fall,f,false);}
            if(f!=ExportWriter.Format.ALL)try(FileOutputStream out=new FileOutputStream(new File(output,"example-"+f.extension+".zip"))) {ExportWriter.write(out,fall,f,true);}
        }
        ExportData empty=new ExportData(List.of(),fall.from,fall.to,fall.zone,null,0);
        try(FileOutputStream out=new FileOutputStream(new File(output,"empty.xlsx"))) {ExportWriter.write(out,empty,ExportWriter.Format.EXCEL,false);}
        System.out.println("PASS: "+checks+" core checks; format fixtures written to "+output);
    }
    private static Session session(long id,long medicine,String taken) {Session s=new Session();s.id=id;s.medicineId=medicine;s.takenAt=at(taken);return s;}
    private static long at(String t){return Instant.parse(t).toEpochMilli();}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void expectFailure(Runnable action,String message){boolean failed=false;try{action.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);}
    private static void reminderChecks(){
        ReminderRule rule=new ReminderRule();rule.times="08:00,20:00";
        next(rule,"2026-11-04T07:00:00Z","2026-11-04T08:00:00Z","UTC","Daily first time");
        next(rule,"2026-11-04T08:00:00Z","2026-11-04T20:00:00Z","UTC","Next time is strictly later than the delivered occurrence");
        next(rule,"2026-11-04T21:00:00Z","2026-11-05T08:00:00Z","UTC","Daily next day");
        rule.frequency="weekly";rule.weekDays=31;rule.times="08:00";
        next(rule,"2026-11-06T21:00:00Z","2026-11-09T08:00:00Z","Europe/Lisbon","Weekdays skip the weekend");
        rule.weekDays=96;next(rule,"2026-11-08T09:00:00Z","2026-11-14T08:00:00Z","Europe/Lisbon","Weekends skip weekdays");
        rule.weekDays=(1<<1)|(1<<4);next(rule,"2026-11-03T09:00:00Z","2026-11-06T08:00:00Z","UTC","Twice-weekly selected days");
        rule.weekDays=0;expectFailure(rule::validate,"Weekly reminder requires a weekday");
        rule.frequency="monthly";rule.monthDays="1,15";
        next(rule,"2026-11-01T09:00:00Z","2026-11-15T08:00:00Z","UTC","Multiple dates each month");
        rule.monthDays="31";next(rule,"2026-01-31T09:00:00Z","2026-03-31T08:00:00Z","UTC","Absent monthly dates are skipped");
        rule.monthDays="0";expectFailure(rule::validate,"Invalid monthly day rejected");
        rule.frequency="yearly";rule.yearDays="--06-01,--12-01";
        next(rule,"2026-06-01T09:00:00Z","2026-12-01T08:00:00Z","UTC","Several annual dates");
        rule.yearDays="--02-29";next(rule,"2096-03-01T00:00:00Z","2104-02-29T08:00:00Z","UTC","Annual leap date spans a non-leap century");
        rule.frequency="daily";rule.times="01:30";
        next(rule,"2026-03-29T00:50:00Z","2026-03-29T01:30:00Z","Europe/Lisbon","DST gap shifts a nonexistent wall-clock time forward");
        next(rule,"2026-10-25T00:45:00Z","2026-10-26T01:30:00Z","Europe/Lisbon","DST overlap does not deliver one wall-clock time twice");
        rule.times="01:30,02:00";next(rule,"2026-03-29T00:50:00Z","2026-03-29T01:00:00Z","Europe/Lisbon","DST shifts do not reorder the earliest actual instant");
        rule.times="";expectFailure(rule::validate,"Reminder requires at least one time");
    }
    private static void next(ReminderRule rule,String now,String expected,String zone,String message){check(rule.nextAfter(at(now),ZoneId.of(zone))==at(expected),message);}
    private static void cycleChecks(){
        ReminderRule r=new ReminderRule();r.frequency="cycle";r.cycleStart="2026-01-01";r.times="08:00,20:00";
        next(r,"2025-12-31T12:00:00Z","2026-01-01T08:00:00Z","UTC","No cycle reminder before its first ON day");
        check(r.isOnDay(LocalDate.parse("2026-01-01"))&&r.cyclePosition(LocalDate.parse("2026-01-21"))==20,"First and twenty-first ON days are inclusive");
        next(r,"2026-01-21T08:00:00Z","2026-01-21T20:00:00Z","UTC","Several daily times remain available on the final ON day");
        next(r,"2026-01-21T20:00:00Z","2026-01-29T08:00:00Z","UTC","21 ON / 7 OFF skips the whole pause");
        next(r,"2026-01-25T15:00:00Z","2026-01-29T08:00:00Z","UTC","Opening during OFF schedules the next ON block");
        check(!r.isOnDay(LocalDate.parse("2026-01-22"))&&!r.isOnDay(LocalDate.parse("2026-01-28"))&&r.isOnDay(LocalDate.parse("2026-01-29")),"Exactly seven OFF days, then automatic restart");
        check(r.cyclePosition(LocalDate.parse("2026-02-26"))==0,"Cycles continue across months without monthly resets");
        check(r.nextOffStart(LocalDate.parse("2026-01-20")).equals(LocalDate.parse("2026-01-22")),"Notification expiry is the first OFF midnight");
        r.cycleOnDays=1;r.cycleOffDays=2;r.times="09:00";next(r,"2026-01-01T09:00:00Z","2026-01-04T09:00:00Z","UTC","One ON and two OFF also supports every-third-day intervals");
        r.cycleOffDays=0;next(r,"2026-01-01T09:00:00Z","2026-01-02T09:00:00Z","UTC","Zero OFF days means continuous daily reminders");
        r.cycleOnDays=21;r.cycleOffDays=7;r.cycleStart="2026-03-08";r.times="08:00";next(r,"2026-03-28T08:00:00Z","2026-04-05T07:00:00Z","Europe/Lisbon","DST during OFF preserves calendar days and local reminder hour");
        r.cycleStart="2028-02-10";next(r,"2028-03-01T08:00:00Z","2028-03-09T08:00:00Z","UTC","Leap day counts as one calendar day");
        r.cycleStart="2030-01-01";next(r,"2026-01-01T00:00:00Z","2030-01-01T08:00:00Z","UTC","A far future anchor is reached without scanning years");
        r.cycleStart="2026-01-01";r.cycleOnDays=1;r.cycleOffDays=3650;next(r,"2026-01-01T08:00:00Z","2035-12-31T08:00:00Z","UTC","Long pauses are jumped directly");
        r.cycleOnDays=0;expectFailure(r::validate,"ON must contain at least one day");r.cycleOnDays=21;r.cycleOffDays=-1;expectFailure(r::validate,"Negative OFF length is rejected");r.cycleOffDays=7;r.cycleStart="";expectFailure(r::validate,"Cycle needs its anchor date");
    }
}
