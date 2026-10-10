package com.example.shortsgesturecontrol;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Owns the durable, battery-friendly reminder schedule and notification state. */
public final class CareReminderScheduler {
    public static final String PREF_ENABLED="care_reminders_enabled";
    public static final String PREF_LAST_SENT="care_reminder_last_sent";
    public static final String CHANNEL_ID="care_reminders";
    public static final int NOTIFICATION_ID=8040;
    static final String ALARM_ACTION="com.example.shortsgesturecontrol.CARE_REMINDER_THRESHOLD";
    private static final int ALARM_REQUEST_CODE=8041;
    private static final String WORK_NAME="care_reminders";
    private static final String ALARM_WORK_NAME="care_reminder_threshold";
    private static final String PREF_NEXT_ALARM_AT="care_reminder_next_alarm_at";
    private static final long CHECK_INTERVAL_MINUTES=15L;

    private CareReminderScheduler() {}

    public static void ensureScheduled(Context context) {
        Context app=context.getApplicationContext();
        ensureChannel(app);
        if(!isEnabled(app))return;
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
            CareReminderWorker.class,CHECK_INTERVAL_MINUTES,TimeUnit.MINUTES)
            .setInitialDelay(CHECK_INTERVAL_MINUTES,TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_NAME,ExistingPeriodicWorkPolicy.KEEP,request);
        scheduleNextThreshold(app);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(PREF_ENABLED,true);
    }

    public static void enable(Context context) {
        Context app=context.getApplicationContext();
        prefs(app).edit().putBoolean(PREF_ENABLED,true).apply();
        ensureScheduled(app);
    }

    public static void disable(Context context) {
        Context app=context.getApplicationContext();
        prefs(app).edit().putBoolean(PREF_ENABLED,false).remove(PREF_LAST_SENT).apply();
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME);
        WorkManager.getInstance(app).cancelUniqueWork(ALARM_WORK_NAME);
        cancelThresholdAlarm(app);
        androidx.core.app.NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID);
    }

    static void enqueueImmediateCheck(Context context) {
        Context app=context.getApplicationContext();
        if(!isEnabled(app))return;
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(CareReminderWorker.class).build();
        WorkManager.getInstance(app).enqueueUniqueWork(
            ALARM_WORK_NAME,ExistingWorkPolicy.REPLACE,request);
    }

    /** Schedules only the next projected threshold; no repeating wake lock is held. */
    public static void scheduleNextThreshold(Context context) {
        Context app=context.getApplicationContext();
        SharedPreferences state=prefs(app);
        if(!isEnabled(app)||!state.getBoolean("created",false)||state.getBoolean("dead",false)) {
            cancelThresholdAlarm(app);
            return;
        }
        long now=System.currentTimeMillis();
        CareReminderPolicy.Snapshot snapshot=new CareReminderPolicy.Snapshot(
            true,state.getBoolean("hatched",false),false,
            new float[]{metric(state,"hunger",78f),metric(state,"joy",82f),metric(state,"energy",74f),metric(state,"clean",88f)},
            state.getLong("last_update",now));
        long next=CareReminderPolicy.nextThresholdAt(snapshot,now);
        long scheduled=state.getLong(PREF_NEXT_ALARM_AT,0L);
        if(next<=0L) {
            cancelThresholdAlarm(app);
            return;
        }
        if(scheduled==next)return;
        AlarmManager manager=app.getSystemService(AlarmManager.class);
        if(manager==null)return;
        PendingIntent pending=thresholdPendingIntent(app);
        manager.cancel(pending);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending);
        state.edit().putLong(PREF_NEXT_ALARM_AT,next).apply();
    }

    public static void clearNotification(Context context) {
        androidx.core.app.NotificationManagerCompat.from(context.getApplicationContext()).cancel(NOTIFICATION_ID);
    }

    private static void cancelThresholdAlarm(Context context) {
        Context app=context.getApplicationContext();
        AlarmManager manager=app.getSystemService(AlarmManager.class);
        if(manager!=null)manager.cancel(thresholdPendingIntent(app));
        prefs(app).edit().remove(PREF_NEXT_ALARM_AT).apply();
    }

    private static PendingIntent thresholdPendingIntent(Context context) {
        Intent intent=new Intent(context,CareReminderAlarmReceiver.class).setAction(ALARM_ACTION);
        return PendingIntent.getBroadcast(context,ALARM_REQUEST_CODE,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static float metric(SharedPreferences prefs,String key,float fallback) {
        try { return prefs.getFloat(key,fallback); }
        catch(ClassCastException ignored) { return prefs.getInt(key,(int)fallback); }
    }

    public static void ensureChannel(Context context) {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;
        NotificationManager manager=context.getApplicationContext().getSystemService(NotificationManager.class);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(
            CHANNEL_ID,"Care reminders",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Reminders when your pet needs care");
        manager.createNotificationChannel(channel);
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("zoey_pet",Context.MODE_PRIVATE);
    }
}
