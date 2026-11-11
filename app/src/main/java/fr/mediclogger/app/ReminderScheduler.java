package fr.mediclogger.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** One local alarm delivers all due rules together, then schedules the next occurrence. */
public final class ReminderScheduler {
    public static final String TICK="fr.mediclogger.REMINDER_TICK";
    public static final String DISMISSED="fr.mediclogger.REMINDER_DISMISSED";
    static final String CHANNEL=ReminderAlerts.LEGACY_CHANNEL;
    private static final String GROUP="medic-logger.reminders";
    private static final int SUMMARY=8001;
    private static final java.util.Set<String> cancelling=new java.util.HashSet<>();
    private static boolean summaryPosted;
    private static final android.os.Handler summaryHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pendingSummary;
    private ReminderScheduler(){}
    public static boolean precise(Context c){AlarmManager alarms=c.getSystemService(AlarmManager.class);return alarms!=null&&(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms());}
    private static PendingIntent alarm(Context c){Intent intent=new Intent(c,ReminderReceiver.class).setAction(TICK).setData(Uri.parse("mediclogger://reminders/tick"));return PendingIntent.getBroadcast(c,8000,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static synchronized void reschedule(Context c,boolean force){
        AlarmManager alarms=c.getSystemService(AlarmManager.class);if(alarms==null)return;long now=System.currentTimeMillis(),earliest=Long.MAX_VALUE;ZoneId zone=ZoneId.systemDefault();
        try(LogStore db=new LogStore(c)){
            for(ReminderRule rule:db.reminders()){
                Models.Medicine medicine=db.medicine(rule.medicineId);
                if(!ReminderAlerts.enabled(c)||!rule.enabled||medicine==null||medicine.archived){db.setReminderNext(rule.id,rule.revision,null);dismiss(c,rule.id);continue;}
                try{
                    boolean off=rule.frequency.equals("cycle")&&!rule.isOnDay(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
                    if(off)dismiss(c,rule.id);
                    long next=!force&&rule.nextAt!=null&&rule.nextAt>rule.lastFiredAt&&(!off||rule.nextAt>now)?rule.nextAt:rule.nextAfter(Math.max(now,rule.lastFiredAt),zone);
                    if(db.setReminderNext(rule.id,rule.revision,next))earliest=Math.min(earliest,next);
                }catch(IllegalArgumentException invalid){db.setReminderNext(rule.id,rule.revision,null);}
            }
        }
        PendingIntent pending=alarm(c);if(earliest==Long.MAX_VALUE){alarms.cancel(pending);return;}
        long trigger=Math.max(now+500,earliest);
        if(precise(c)){
            try{alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending);return;}catch(SecurityException revoked){/* Access can change after the check. */}
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending);
    }
    public static synchronized void deliver(Context original){
        Context c=AppPrefs.localized(original);long now=System.currentTimeMillis();
        if(!ReminderAlerts.enabled(c)){reschedule(c,false);return;}
        try(LogStore db=new LogStore(c)){
            for(ReminderRule rule:db.reminders()){
                if(rule.frequency.equals("cycle")&&!rule.isOnDay(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate())){
                    if(rule.nextAt!=null&&rule.nextAt<=now+1000)db.setReminderNext(rule.id,rule.revision,null);dismiss(c,rule.id);continue;
                }
                if(!rule.enabled||rule.nextAt==null||rule.nextAt>now+1000)continue;
                Models.Medicine medicine=db.medicine(rule.medicineId);
                if(medicine==null||medicine.archived)continue;
                long expected=rule.nextAt;
                if(db.claimReminder(rule.id,rule.revision,expected))show(c,rule,medicine,expected);
            }
        }
        refreshSummary(c);reschedule(c,false);
    }
    static void createChannel(Context original){
        ReminderAlerts.ensureChannel(original);
    }
    @SuppressLint("MissingPermission") // Notification permission is checked before posting.
    private static void show(Context c,ReminderRule rule,Models.Medicine medicine,long due){
        if(!ReminderAlerts.enabled(c)||!StatusNotifications.allowed(c))return;NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
        String channel=ReminderAlerts.ensureChannel(c);
        Intent open=new Intent(c,MainActivity.class).setAction("fr.mediclogger.REMINDER_DETAILS").setData(Uri.parse("mediclogger://reminder/"+rule.id)).putExtra("medicine_id",medicine.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,9000,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent log=new Intent(c,MainActivity.class).setAction("fr.mediclogger.REMINDER_OPEN").setData(Uri.parse("mediclogger://reminder/"+rule.id+"/log")).putExtra("medicine_id",medicine.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent logDose=PendingIntent.getActivity(c,9001,log,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent removed=new Intent(c,ReminderReceiver.class).setAction(DISMISSED).setData(Uri.parse("mediclogger://reminder/"+rule.id+"/dismiss")).putExtra("rule_id",rule.id);
        PendingIntent delete=PendingIntent.getBroadcast(c,9002,removed,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String time=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(AppPrefs.locale(c)).format(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()));
        String body=I18n.tr(c,"Scheduled for %s",time)+(medicine.dose.isEmpty()?"":" · "+medicine.dose);
        Notification publicVersion=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle("Medic Logger").setContentText(I18n.tr(c,"Medication reminders")).setVisibility(Notification.VISIBILITY_PUBLIC).build();
        Notification.Builder builder=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setColor(medicine.color).setContentTitle(medicine.name).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(I18n.tr(c,"Time to take %s",medicine.label())+"\n"+body)).setContentIntent(content).setAutoCancel(true).setWhen(due)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setCategory(Notification.CATEGORY_REMINDER).setDeleteIntent(delete)
            .setGroup(GROUP).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .addAction(new Notification.Action.Builder(null,I18n.tr(c,"Log dose"),logDose).build());
        if(rule.frequency.equals("cycle")&&rule.cycleOffDays>0){long stop=rule.nextOffStart(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();builder.setTimeoutAfter(Math.max(1,stop-System.currentTimeMillis()));}
        Notification notification=builder.build();
        try{cancelling.remove("reminder-"+rule.id);manager.notify("reminder-"+rule.id,1,notification);}catch(SecurityException ignored){}
    }
    public static synchronized void dismiss(Context c,long id){NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager!=null){cancelling.add("reminder-"+id);manager.cancel("reminder-"+id,1);}refreshSummary(c);}
    public static synchronized void dismissMedicine(Context c,long medicineId){
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
        try(LogStore db=new LogStore(c)){for(ReminderRule rule:db.reminders(medicineId)){cancelling.add("reminder-"+rule.id);manager.cancel("reminder-"+rule.id,1);}}
        refreshSummary(c);
    }
    static synchronized void refreshSummary(Context original){
        updateSummaryNow(original);
        // notify/cancel enqueue work inside Android; getActiveNotifications is not a
        // read-after-write barrier. Reconcile once the notification service has applied it.
        if(pendingSummary!=null)summaryHandler.removeCallbacks(pendingSummary);
        Context application=original.getApplicationContext();
        pendingSummary=()->{synchronized(ReminderScheduler.class){pendingSummary=null;updateSummaryNow(application);}};
        summaryHandler.postDelayed(pendingSummary,250);
    }
    @SuppressLint("MissingPermission") // allowed() checks the runtime permission before posting.
    private static void updateSummaryNow(Context original){
        Context c=AppPrefs.localized(original);NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
        java.util.List<Notification> children=new java.util.ArrayList<>();
        java.util.Set<String> visibleTags=new java.util.HashSet<>();boolean hasSummary=summaryPosted;
        for(android.service.notification.StatusBarNotification posted:manager.getActiveNotifications()){
            if(posted.getId()==SUMMARY)hasSummary=true;
            if(posted.getId()==1&&posted.getTag()!=null&&posted.getTag().startsWith("reminder-")){
                visibleTags.add(posted.getTag());if(!cancelling.contains(posted.getTag()))children.add(posted.getNotification());
            }
        }
        cancelling.retainAll(visibleTags);
        if(children.isEmpty()||!StatusNotifications.allowed(c)){summaryPosted=false;manager.cancel(SUMMARY);return;}
        // Android also cancels children when their summary is removed. Keep an existing
        // summary while one child remains, and update its count instead of cancelling it.
        if(children.size()==1&&!hasSummary)return;
        String channel=ReminderAlerts.ensureChannel(c);String title=I18n.tr(c,"Medication reminders");String count=I18n.tr(c,"%d reminders",children.size());
        Notification.InboxStyle inbox=new Notification.InboxStyle().setBigContentTitle(title).setSummaryText(count);
        for(Notification child:children){CharSequence name=child.extras.getCharSequence(Notification.EXTRA_TITLE),body=child.extras.getCharSequence(Notification.EXTRA_TEXT);inbox.addLine(name+" · "+body);}
        Intent open=new Intent(c,MainActivity.class).setAction("fr.mediclogger.SHOW_STATUS").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(c,9003,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle("Medic Logger").setContentText(title).setVisibility(Notification.VISIBILITY_PUBLIC).build();
        Notification summary=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setColor(0xffb63840).setContentTitle(title).setContentText(count)
            .setStyle(inbox).setContentIntent(content).setGroup(GROUP).setGroupSummary(true).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true).setAutoCancel(false).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setCategory(Notification.CATEGORY_REMINDER).build();
        try{summaryPosted=true;manager.notify(SUMMARY,summary);}catch(SecurityException ignored){summaryPosted=false;manager.cancel(SUMMARY);}
    }
}
