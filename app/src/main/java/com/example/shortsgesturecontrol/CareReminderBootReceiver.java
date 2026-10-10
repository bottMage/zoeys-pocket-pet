package com.example.shortsgesturecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the closed-app reminder schedule after Android restarts. */
public final class CareReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            CareReminderScheduler.ensureScheduled(context);
        }
    }
}
