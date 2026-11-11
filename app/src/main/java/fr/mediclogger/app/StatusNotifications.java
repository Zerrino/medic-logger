package fr.mediclogger.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import fr.mediclogger.app.Models.Session;

/** A real notification, not a foreground service or a fake media player. All actions are local. */
public final class StatusNotifications {
    private static final String CHANNEL="dose-status";
    private static final int ID=410;
    private StatusNotifications(){}
    public static boolean allowed(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    public static List<Session> active(LogStore db){List<Session> list=new ArrayList<>();for(Session s:db.sessions())if(s.active())list.add(s);return list;}
    @SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS before notify().
    public static void update(Context original){
        Context c=AppPrefs.localized(original);NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
        if(!AppPrefs.notifications(c)||!allowed(c)){manager.cancel(ID);return;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,I18n.tr(c,"Medication status"),NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(I18n.tr(c,"Active doses and quick controls"));channel.setShowBadge(false);channel.setSound(null,null);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager.createNotificationChannel(channel);
        List<Session> list;Map<Long,Integer> colors=new HashMap<>();try(LogStore db=new LogStore(c)){list=active(db);for(Models.Medicine medicine:db.medicines(true))colors.put(medicine.id,medicine.color);}
        if(list.isEmpty()){manager.cancel(ID);return;}
        long selected=AppPrefs.get(c).getLong("notification_session",-1);int position=0;
        for(int i=0;i<list.size();i++)if(list.get(i).id==selected)position=i;
        Session s=list.get(position);AppPrefs.get(c).edit().putLong("notification_session",s.id).apply();
        Session previous=list.get(Math.floorMod(position-1,list.size())),next=list.get((position+1)%list.size());
        int color=colors.getOrDefault(s.medicineId,s.color);
        // Notification theme attributes do not resolve reliably in custom views, so pick readable tones from the system theme.
        boolean night=(c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int secondary=night?0xffc9c1c3:0xff5d5154,primary=night?0xfff3eaea:0xff302326;
        String taken=time(c,s.takenAt),phase=time(c,s.onsetAt==null?s.takenAt:s.onsetAt);
        String detail=(s.dose.isEmpty()?"":s.dose+" · ")+I18n.tr(c,"Taken at %s",taken);
        if(!s.alias.isEmpty())detail=s.medicineName+" · "+detail;
        String description=I18n.tr(c,s.onsetAt==null?"Waiting since %s":"Effects since %s",phase);
        Intent open=new Intent(c,MainActivity.class).setAction("fr.mediclogger.SHOW_STATUS").putExtra("session_id",s.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,100,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        RemoteViews expanded=new RemoteViews(c.getPackageName(),R.layout.notification_dose);
        expanded.setTextViewText(R.id.notification_name,s.label());expanded.setTextViewText(R.id.notification_status,description);expanded.setTextViewText(R.id.notification_detail,detail);
        expanded.setInt(R.id.notification_marker,"setColorFilter",color);
        expanded.setTextColor(R.id.notification_detail,secondary);expanded.setTextColor(R.id.notification_position,secondary);
        expanded.setInt(R.id.notification_previous,"setColorFilter",secondary);expanded.setInt(R.id.notification_next,"setColorFilter",secondary);
        expanded.setViewVisibility(R.id.notification_carousel,list.size()>1?View.VISIBLE:View.GONE);
        expanded.setTextViewText(R.id.notification_position,I18n.tr(c,"%d of %d doses",position+1,list.size()));
        expanded.setContentDescription(R.id.notification_previous,I18n.tr(c,"Previous medicine")+": "+previous.label());expanded.setContentDescription(R.id.notification_next,I18n.tr(c,"Next medicine")+": "+next.label());
        expanded.setOnClickPendingIntent(R.id.notification_previous,action(c,"previous",s.id,101));expanded.setOnClickPendingIntent(R.id.notification_next,action(c,"next",s.id,102));
        RemoteViews compact=new RemoteViews(c.getPackageName(),R.layout.notification_compact);
        compact.setTextViewText(R.id.notification_name,s.label());compact.setTextViewText(R.id.notification_status,I18n.tr(c,s.onsetAt==null?"Waiting":"Effects active")+(list.size()>1?" · "+I18n.tr(c,"%d of %d doses",position+1,list.size()):""));
        compact.setTextViewText(R.id.notification_active,I18n.tr(c,"Active"));compact.setTextViewText(R.id.notification_over,I18n.tr(c,"Over"));
        compact.setInt(R.id.notification_previous,"setColorFilter",secondary);compact.setInt(R.id.notification_next,"setColorFilter",secondary);
        compact.setTextColor(R.id.notification_active,primary);compact.setTextColor(R.id.notification_over,primary);
        compact.setViewVisibility(R.id.notification_previous,list.size()>1?View.VISIBLE:View.GONE);compact.setViewVisibility(R.id.notification_next,list.size()>1?View.VISIBLE:View.GONE);
        compact.setViewVisibility(R.id.notification_active,list.size()==1&&s.onsetAt==null?View.VISIBLE:View.GONE);compact.setViewVisibility(R.id.notification_over,list.size()==1?View.VISIBLE:View.GONE);
        compact.setInt(R.id.notification_marker,"setColorFilter",color);
        compact.setContentDescription(R.id.notification_previous,I18n.tr(c,"Previous medicine")+": "+previous.label());compact.setContentDescription(R.id.notification_next,I18n.tr(c,"Next medicine")+": "+next.label());
        compact.setOnClickPendingIntent(R.id.notification_previous,action(c,"previous",s.id,101));compact.setOnClickPendingIntent(R.id.notification_next,action(c,"next",s.id,102));
        compact.setOnClickPendingIntent(R.id.notification_active,action(c,"active",s.id,103));compact.setOnClickPendingIntent(R.id.notification_over,finishAction(c,s.id));
        Notification publicVersion=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Medic Logger").setContentText(I18n.tr(c,"Medication status")).setVisibility(Notification.VISIBILITY_PUBLIC).build();
        Notification.Builder builder=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setColor(color)
            .setContentTitle(s.label()).setContentText(description).setContentIntent(content)
            .setCustomContentView(compact).setCustomBigContentView(expanded).setStyle(new Notification.DecoratedCustomViewStyle()).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS).setOngoing(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion);
        if(s.onsetAt==null)builder.addAction(new Notification.Action.Builder(null,I18n.tr(c,"Active"),action(c,"active",s.id,103)).build());
        builder.addAction(new Notification.Action.Builder(null,I18n.tr(c,"Over"),finishAction(c,s.id)).build());
        try{manager.notify(ID,builder.build());}catch(SecurityException ignored){manager.cancel(ID);}
    }
    private static String time(Context c,long at){
        java.time.ZonedDateTime date=Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault());
        return DateTimeFormatter.ofPattern(date.toLocalDate().equals(java.time.LocalDate.now())?"HH:mm":"d MMM HH:mm",AppPrefs.locale(c)).format(date);
    }
    private static PendingIntent action(Context c,String action,long id,int request){Intent i=new Intent(c,NotificationActions.class).setAction("fr.mediclogger."+action).setData(Uri.parse("mediclogger://dose/"+id+"/"+action)).putExtra("session_id",id);return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent finishAction(Context c,long id){Intent intent=new Intent(c,MainActivity.class).setAction("fr.mediclogger.FINISH_DOSE").setData(Uri.parse("mediclogger://dose/"+id+"/finish")).putExtra("session_id",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,104,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
