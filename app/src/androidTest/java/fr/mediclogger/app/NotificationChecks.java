package fr.mediclogger.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.util.List;
import java.util.function.Predicate;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;

final class NotificationChecks {
    private static int checks;
    static int run(Context c)throws Exception{
        if(!StatusNotifications.allowed(c))return 0;
        boolean enabled=AppPrefs.notifications(c);long selected=AppPrefs.get(c).getLong("notification_session",-1);
        Medicine a=new Medicine(),b=new Medicine();Session first=null,second=null;
        a.name="TEST Notification A "+System.currentTimeMillis();b.name="TEST Notification B "+System.currentTimeMillis();a.color=0xffc93048;b.color=0xff5377ae;
        a.monitorEffects=true;b.monitorEffects=true;
        try(LogStore db=new LogStore(c)){
            try{
                db.saveMedicine(a);db.saveMedicine(b);first=db.take(a.id);second=db.take(b.id);
                AppPrefs.get(c).edit().putBoolean("notifications",true).putLong("notification_session",first.id).commit();StatusNotifications.update(c);
                Notification old=notification(c,first.label(),2);check(old.actions.length==2,"Waiting notification exposes Active and Over");
                View layout=old.bigContentView.apply(c,null);List<Session> ordered=StatusNotifications.active(db);int selectedIndex=0;for(int n=0;n<ordered.size();n++)if(ordered.get(n).id==first.id)selectedIndex=n;
                Session previous=ordered.get(Math.floorMod(selectedIndex-1,ordered.size())),next=ordered.get((selectedIndex+1)%ordered.size());
                check(buttonColor(c,layout,R.id.notification_previous)==db.medicine(previous.medicineId).color,"Previous arrow uses the destination medicine color");
                check(buttonColor(c,layout,R.id.notification_next)==db.medicine(next.medicineId).color,"Next arrow uses the destination medicine color");
                Notification.Action oldActive=old.actions[0];
                AppPrefs.get(c).edit().putLong("notification_session",second.id).commit();StatusNotifications.update(c);
                Notification current=notification(c,second.label(),2);check(!oldActive.actionIntent.equals(current.actions[0].actionIntent),"Actions have distinct dose identities");
                oldActive.actionIntent.send();waitFor(db,first.id,s->s.onsetAt!=null);check(db.session(second.id).onsetAt==null,"A stale action stays bound to its original dose");
                current.actions[0].actionIntent.send();waitFor(db,second.id,s->s.onsetAt!=null);check(notification(c,second.label(),1).actions.length==1,"Active doses expose only Over");
                List<Session> list=StatusNotifications.active(db);int index=0;for(int n=0;n<list.size();n++)if(list.get(n).id==second.id)index=n;
                long expected=list.get((index+1)%list.size()).id;
                new NotificationActions().onReceive(c,new Intent("fr.mediclogger.next").putExtra("session_id",second.id));check(AppPrefs.get(c).getLong("notification_session",-1)==expected,"Next control cycles through active doses");
                new NotificationActions().onReceive(c,new Intent("fr.mediclogger.previous").putExtra("session_id",expected));check(AppPrefs.get(c).getLong("notification_session",-1)==second.id,"Previous control returns to the prior dose");
                current.actions[1].actionIntent.send();waitFor(db,second.id,s->s.endedAt!=null);long end=db.session(second.id).endedAt;
                current.actions[1].actionIntent.send();SystemClock.sleep(80);check(db.session(second.id).endedAt==end&&db.session(first.id).active(),"Over is idempotent and affects only its displayed dose");
            }finally{
                if(first!=null)db.deleteSession(first.id);if(second!=null)db.deleteSession(second.id);if(a.id!=0)db.deleteMedicine(a.id);if(b.id!=0)db.deleteMedicine(b.id);
                AppPrefs.get(c).edit().putBoolean("notifications",enabled).putLong("notification_session",selected).commit();StatusNotifications.update(c);
            }
        }
        return checks;
    }
    private static Notification notification(Context c,String label,int actions){
        for(int i=0;i<100;i++){for(StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==410){Notification value=n.getNotification();if(label.equals(value.extras.getString(Notification.EXTRA_TITLE))&&value.actions!=null&&value.actions.length==actions)return value;}SystemClock.sleep(20);}
        throw new AssertionError("The expected status notification did not finish updating");
    }
    private static void waitFor(LogStore db,long id,Predicate<Session> condition){for(int i=0;i<100;i++){if(condition.test(db.session(id)))return;SystemClock.sleep(20);}throw new AssertionError("Notification action was not persisted");}
    private static int buttonColor(Context c,View layout,int id){ImageButton button=layout.findViewById(id);android.graphics.Bitmap image=((BitmapDrawable)button.getDrawable()).getBitmap();int sample=Math.round(10*c.getResources().getDisplayMetrics().density);return image.getPixel(sample,sample);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
