package fr.mediclogger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;
import fr.mediclogger.app.Models.Session;

public final class NotificationActions extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!AppPrefs.notifications(context))return;
        String action=intent.getAction();if(action==null)return;
        try(LogStore db=new LogStore(context)){
            long id=intent.getLongExtra("session_id",-1);
            if(action.equals("fr.mediclogger.previous")||action.equals("fr.mediclogger.next")){
                List<Session> list=StatusNotifications.active(db);int index=0;for(int i=0;i<list.size();i++)if(list.get(i).id==id)index=i;
                if(!list.isEmpty()){index=Math.floorMod(index+(action.endsWith("next")?1:-1),list.size());AppPrefs.get(context).edit().putLong("notification_session",list.get(index).id).apply();}
            }else if(action.equals("fr.mediclogger.active")||action.equals("fr.mediclogger.over")){
                Session s=db.session(id);
                if(s!=null&&s.active()&&(!action.endsWith("active")||s.onsetAt==null))db.mark(id,action.endsWith("active"));
            }
        }catch(IllegalArgumentException ignored){/* A stale notification cannot overwrite a newer observation. */}
        StatusNotifications.update(context);
    }
}
