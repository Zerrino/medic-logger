package fr.mediclogger.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** User-selected alert profiles use separate Android channels, whose behavior is immutable. */
final class ReminderAlerts {
    static final String LEGACY_CHANNEL="medication-reminders";
    static final int SYSTEM=0,SOUND=1,VIBRATION=2,SOUND_AND_VIBRATION=3,SILENT=4;
    static final String[] LABELS={"System default","Sound","Vibration","Sound and vibration","Silent"};
    private ReminderAlerts(){}
    static boolean enabled(Context c){return AppPrefs.get(c).getBoolean("reminders_enabled",true);}
    static int mode(Context c){int mode=AppPrefs.get(c).getInt("reminder_alert_mode",SYSTEM);return mode>=0&&mode<LABELS.length?mode:SYSTEM;}
    static boolean hasSound(Context c){return mode(c)==SOUND||mode(c)==SOUND_AND_VIBRATION;}
    static Uri sound(Context c){return Uri.parse(AppPrefs.get(c).getString("reminder_sound",RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()));}
    static String channelId(Context c){
        int mode=mode(c);if(mode==SYSTEM)return LEGACY_CHANNEL;
        if(!hasSound(c))return "reminder-alert-"+mode;
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(sound(c).toString().getBytes(StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();
            for(int i=0;i<8;i++)hash.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]&255));
            return "reminder-alert-"+mode+"-"+hash;
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static String soundName(Context c){
        try{android.media.Ringtone ringtone=RingtoneManager.getRingtone(c,sound(c));if(ringtone!=null)return ringtone.getTitle(c);}catch(RuntimeException unavailable){}
        return I18n.tr(c,"System default");
    }
    static String ensureChannel(Context original){
        Context c=AppPrefs.localized(original);NotificationManager manager=c.getSystemService(NotificationManager.class);String id=channelId(c);if(manager==null)return id;
        int mode=mode(c);String label=I18n.tr(c,"Medication reminders")+(mode==SYSTEM?"":" · "+I18n.tr(c,LABELS[mode]));
        NotificationChannel channel=new NotificationChannel(id,label,mode==SILENT?NotificationManager.IMPORTANCE_LOW:NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(I18n.tr(c,"Scheduled medication reminders"));channel.setShowBadge(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        if(mode!=SYSTEM){
            channel.setSound(hasSound(c)?sound(c):null,hasSound(c)?new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build():null);
            channel.enableVibration(mode==VIBRATION||mode==SOUND_AND_VIBRATION);
        }
        manager.createNotificationChannel(channel);return id;
    }
}
