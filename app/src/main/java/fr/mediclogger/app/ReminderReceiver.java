package fr.mediclogger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(ReminderScheduler.TICK.equals(intent.getAction()))ReminderScheduler.deliver(context);
        else if(ReminderScheduler.DISMISSED.equals(intent.getAction()))ReminderScheduler.dismiss(context,intent.getLongExtra("rule_id",-1));
        else ReminderScheduler.reschedule(context,true);
    }
}
