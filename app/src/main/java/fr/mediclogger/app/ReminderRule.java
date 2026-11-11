package fr.mediclogger.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeSet;

public final class ReminderRule {
    public long id, medicineId, revision, lastFiredAt;
    public String frequency="daily", times="08:00", monthDays="1", yearDays="--01-01";
    public int weekDays=127;
    public int cycleOnDays=21,cycleOffDays=7;
    public String cycleStart=LocalDate.now().toString();
    public boolean enabled=true;
    public Long nextAt;

    public Set<LocalTime> times(){Set<LocalTime> out=new TreeSet<>();for(String item:times.split(","))if(!item.trim().isEmpty())out.add(LocalTime.parse(item.trim()));return out;}
    public Set<Integer> monthDays(){Set<Integer> out=new TreeSet<>();for(String item:monthDays.split(","))if(!item.trim().isEmpty())out.add(Integer.parseInt(item.trim()));return out;}
    public Set<MonthDay> yearDays(){Set<MonthDay> out=new TreeSet<>();for(String item:yearDays.split(","))if(!item.trim().isEmpty())out.add(MonthDay.parse(item.trim()));return out;}
    public void validate(){
        if(times().isEmpty())throw new IllegalArgumentException("Add at least one reminder time.");
        if(!frequency.equals("daily")&&!frequency.equals("weekly")&&!frequency.equals("monthly")&&!frequency.equals("yearly")&&!frequency.equals("cycle"))throw new IllegalArgumentException("Choose a reminder frequency.");
        if(frequency.equals("weekly")&&(weekDays&127)==0)throw new IllegalArgumentException("Choose at least one weekday.");
        if(frequency.equals("monthly")){Set<Integer> days=monthDays();if(days.isEmpty()||days.stream().anyMatch(day->day<1||day>31))throw new IllegalArgumentException("Choose valid days of the month.");}
        if(frequency.equals("yearly")&&yearDays().isEmpty())throw new IllegalArgumentException("Choose at least one annual date.");
        if(frequency.equals("cycle"))validateCycle();
    }
    private void validateCycle(){
        if(cycleOnDays<1||cycleOnDays>3650||cycleOffDays<0||cycleOffDays>3650)throw new IllegalArgumentException("Use 1–3650 ON days and 0–3650 OFF days.");
        cycleStartDate();
    }
    public LocalDate cycleStartDate(){try{return LocalDate.parse(cycleStart);}catch(java.time.DateTimeException|NullPointerException invalid){throw new IllegalArgumentException("Choose a valid first ON day.");}}
    /** Zero-based position in the repeating calendar-day cycle; -1 before its first day. */
    public int cyclePosition(LocalDate date){validateCycle();long days=ChronoUnit.DAYS.between(cycleStartDate(),date);return days<0?-1:(int)(days%(cycleOnDays+cycleOffDays));}
    public boolean isOnDay(LocalDate date){int position=cyclePosition(date);return position>=0&&position<cycleOnDays;}
    public LocalDate nextOffStart(LocalDate date){int position=cyclePosition(date);return position<0?cycleStartDate().plusDays(cycleOnDays):date.plusDays(cycleOnDays-position);}
    /** Local wall-clock recurrence: one occurrence per selected time, including DST transitions. */
    public long nextAfter(long now,ZoneId zone){
        validate();Set<LocalTime> hours=times();Set<Integer> monthly=frequency.equals("monthly")?monthDays():java.util.Collections.emptySet();Set<MonthDay> yearly=frequency.equals("yearly")?yearDays():java.util.Collections.emptySet();
        if(frequency.equals("cycle")){
            LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),anchor=cycleStartDate();if(date.isBefore(anchor))date=anchor;
            // Jump directly over an OFF block, including long pauses and future anchors.
            while(true){int position=cyclePosition(date);if(position>=cycleOnDays)date=date.plusDays(cycleOnDays+cycleOffDays-position);
                long earliest=Long.MAX_VALUE;for(LocalTime time:hours){long at=date.atTime(time).atZone(zone).toInstant().toEpochMilli();if(at>now&&at<earliest)earliest=at;}
                if(earliest!=Long.MAX_VALUE)return earliest;date=date.plusDays(1);
            }
        }
        LocalDate start=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();int limit=frequency.equals("yearly")?366*9:frequency.equals("monthly")?370:8;
        for(int offset=0;offset<limit;offset++){
            LocalDate date=start.plusDays(offset);
            boolean matches=switch(frequency){case "daily"->true;case "weekly"->(weekDays&(1<<(date.getDayOfWeek().getValue()-1)))!=0;case "monthly"->monthly.contains(date.getDayOfMonth());case "yearly"->yearly.contains(MonthDay.from(date));default->false;};
            if(!matches)continue;long earliest=Long.MAX_VALUE;
            for(LocalTime time:hours){long at=date.atTime(time).atZone(zone).toInstant().toEpochMilli();if(at>now&&at<earliest)earliest=at;}
            if(earliest!=Long.MAX_VALUE)return earliest;
        }
        throw new IllegalArgumentException("No upcoming reminder date was found.");
    }
}
