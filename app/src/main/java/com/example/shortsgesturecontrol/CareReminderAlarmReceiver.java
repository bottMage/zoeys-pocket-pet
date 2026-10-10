package com.example.shortsgesturecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes the app at the next projected low-stat threshold without keeping a service alive. */
public final class CareReminderAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(CareReminderScheduler.ALARM_ACTION.equals(intent.getAction())) {
            CareReminderScheduler.enqueueImmediateCheck(context);
        }
    }
}
