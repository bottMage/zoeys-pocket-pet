package com.example.shortsgesturecontrol;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Evaluates saved progress while the app process is not running. */
public final class CareReminderWorker extends Worker {
    public CareReminderWorker(Context context,WorkerParameters parameters) {
        super(context,parameters);
    }

    @Override public Result doWork() {
        Context context=getApplicationContext();
        SharedPreferences prefs=CareReminderScheduler.prefs(context);
        if(!prefs.getBoolean(CareReminderScheduler.PREF_ENABLED,true))return finish(context);
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(
            context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            return finish(context);
        }
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return finish(context);

        long now=System.currentTimeMillis();
        CareReminderPolicy.Snapshot snapshot=new CareReminderPolicy.Snapshot(
            prefs.getBoolean("created",false),prefs.getBoolean("hatched",false),prefs.getBoolean("dead",false),
            new float[]{metric(prefs,"hunger",78f),metric(prefs,"joy",82f),metric(prefs,"energy",74f),metric(prefs,"clean",88f)},
            prefs.getLong("last_update",now));
        long lastSent=prefs.getLong(CareReminderScheduler.PREF_LAST_SENT,0L);
        CareReminderPolicy.Evaluation evaluation=CareReminderPolicy.evaluate(snapshot,now,lastSent);
        boolean sick=prefs.getBoolean("sick",false);
        if(evaluation.recovered) {
            prefs.edit().remove(CareReminderScheduler.PREF_LAST_SENT).apply();
            CareReminderScheduler.clearNotification(context);
        }
        if(!sick) {
            prefs.edit().remove(CareReminderScheduler.PREF_LAST_SICK_SENT).apply();
            CareReminderScheduler.clearSickNotification(context);
        } else if(snapshot.created&&!snapshot.dead&&CareReminderPolicy.shouldSendSicknessReminder(
            true,now,prefs.getLong(CareReminderScheduler.PREF_LAST_SICK_SENT,0L))) {
            sendSicknessNotification(context,prefs,now);
            return finish(context);
        }
        if(evaluation.shouldNotify) {
            sendCareNotification(context,snapshot,evaluation,prefs,now);
            return finish(context);
        }
        if(prefs.getBoolean(CareReminderScheduler.PREF_APP_VISIBLE,false))return finish(context);

        long lastActivity=Math.max(
            prefs.getLong(CareReminderScheduler.PREF_LAST_ACTIVITY,0L),
            prefs.getLong("saved_at",0L));
        if(lastActivity<=0)lastActivity=snapshot.lastUpdate;
        long lastGeneral=prefs.getLong(CareReminderScheduler.PREF_LAST_GENERAL_SENT,0L);
        if(!CareReminderPolicy.shouldSendGeneralReminder(
            snapshot.created,snapshot.dead,now,lastActivity,lastGeneral))return finish(context);

        sendGeneralNotification(context,snapshot,prefs,now);
        return finish(context);
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void sendCareNotification(Context context,CareReminderPolicy.Snapshot snapshot,
                                             CareReminderPolicy.Evaluation evaluation,
                                             SharedPreferences prefs,long now) {

        CareReminderScheduler.ensureChannel(context);
        CareReminderScheduler.clearGeneralNotification(context);
        String name=prefs.getString("name","Mochi");
        String[] labels=snapshot.hatched
            ? new String[]{"hunger","joy","energy","cleanliness"}
            : new String[]{"warmth","comfort","rest","nest"};
        StringBuilder low=new StringBuilder();
        for(int index=0;index<4;index++)if((evaluation.lowMask&(1<<index))!=0) {
            if(low.length()>0)low.append(" and ");
            low.append(labels[index]);
        }
        String body;
        if(evaluation.urgent&&low.length()>0) {
            body=name+" needs "+low+" now. Ongoing low care is affecting growth.";
        } else if(evaluation.urgent) {
            body=name+" needs care now. Ongoing low care is affecting growth.";
        } else if(evaluation.careRisk&&low.length()>0) {
            body=name+" may need "+low+". Overall care is starting to affect growth.";
        } else if(evaluation.careRisk) {
            body=name+" could use some care soon so growth stays on track.";
        } else {
            body=name+" may need "+low+". A little attention would help.";
        }
        Intent intent=new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,CareReminderScheduler.NOTIFICATION_ID,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification=new NotificationCompat.Builder(context,CareReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MochiGotchi care reminder")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(CareReminderScheduler.NOTIFICATION_ID,notification.build());
        // A care alert counts as a useful nudge too, so do not stack a cute
        // general reminder immediately behind an urgent care notification.
        prefs.edit().putLong(CareReminderScheduler.PREF_LAST_SENT,now)
            .putLong(CareReminderScheduler.PREF_LAST_GENERAL_SENT,now).apply();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void sendGeneralNotification(Context context,CareReminderPolicy.Snapshot snapshot,
                                                SharedPreferences prefs,long now) {
        String name=prefs.getString("name","Mochi");
        String[] messages=snapshot.hatched
            ? new String[]{
                "I miss you! Come play with me when you can.",
                "I have a happy wiggle waiting for you!",
                "Come say hi when you have a moment!"}
            : new String[]{
                "Your little egg is waiting for a visit!",
                "I wonder when you will come check on me!",
                "Come say hi to your cozy little egg!"};
        int messageIndex=(int)((now/CareReminderPolicy.GENERAL_REMINDER_AFTER_MILLIS)%messages.length);
        String body=name+" says: "+messages[messageIndex];
        Intent intent=new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,CareReminderScheduler.GENERAL_NOTIFICATION_ID,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification=new NotificationCompat.Builder(context,CareReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MochiGotchi says hi!")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(CareReminderScheduler.GENERAL_NOTIFICATION_ID,notification.build());
        prefs.edit().putLong(CareReminderScheduler.PREF_LAST_GENERAL_SENT,now).apply();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void sendSicknessNotification(Context context,SharedPreferences prefs,long now) {
        String name=prefs.getString("name","Mochi");
        String[] symptoms={"tummy ache","feeling lonely","being very sleepy","itchiness"};
        int symptom=prefs.getInt("symptom",PetLife.TUMMY_ACHE);
        symptom=Math.max(0,Math.min(symptoms.length-1,symptom));
        String body=name+" isn't feeling well — I have a "+symptoms[symptom]+". Please give me medicine.";
        Intent intent=new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,CareReminderScheduler.SICK_NOTIFICATION_ID,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification=new NotificationCompat.Builder(context,CareReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MochiGotchi needs a little help")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(CareReminderScheduler.SICK_NOTIFICATION_ID,notification.build());
        prefs.edit().putLong(CareReminderScheduler.PREF_LAST_SICK_SENT,now)
            .putLong(CareReminderScheduler.PREF_LAST_GENERAL_SENT,now).apply();
    }

    private static Result finish(Context context) {
        CareReminderScheduler.scheduleNextThreshold(context);
        return Result.success();
    }

    private static float metric(SharedPreferences prefs,String key,float fallback) {
        try { return prefs.getFloat(key,fallback); }
        catch(ClassCastException ignored) { return prefs.getInt(key,(int)fallback); }
    }
}
