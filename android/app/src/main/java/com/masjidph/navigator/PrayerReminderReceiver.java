package com.masjidph.navigator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PrayerReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "daily_prayer_reminders";
    private static final String ACTION_PREFIX = "com.masjidph.navigator.PRAYER_REMINDER.";
    private static final String EXTRA_PRAYER = "prayer_name";
    private static final String[] PRAYERS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        SharedPreferences prefs = context.getSharedPreferences(
                PrayerScheduleWorker.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PrayerScheduleWorker.KEY_ENABLED, false)) return;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String prayer = intent.getStringExtra(EXTRA_PRAYER);
        if (!isKnownPrayer(prayer)) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Daily prayer reminders", NotificationManager.IMPORTANCE_DEFAULT));
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 1500 + prayerIndex(prayer), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(prayer + " prayer")
                .setContentText("It is time for " + prayer + ". Confirm the timetable with your local masjid.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(1500 + prayerIndex(prayer), notification);
    }

    static void schedule(Context context, String prayer, long atMillis) {
        if (!isKnownPrayer(prayer) || atMillis <= System.currentTimeMillis()) return;
        android.app.AlarmManager alarms =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = alarmPendingIntent(context, prayer, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 23) {
            alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMillis, pending);
        } else {
            alarms.set(android.app.AlarmManager.RTC_WAKEUP, atMillis, pending);
        }
    }

    static void cancelAll(Context context) {
        android.app.AlarmManager alarms =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String prayer : PRAYERS) {
            PendingIntent pending = alarmPendingIntent(context, prayer, PendingIntent.FLAG_NO_CREATE);
            if (pending != null) {
                if (alarms != null) alarms.cancel(pending);
                pending.cancel();
            }
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) for (int i = 0; i < PRAYERS.length; i++) manager.cancel(1500 + i);
    }

    private static PendingIntent alarmPendingIntent(Context context, String prayer, int flag) {
        int flags = flag | PendingIntent.FLAG_IMMUTABLE;
        Intent alarm = new Intent(context, PrayerReminderReceiver.class)
                .setAction(ACTION_PREFIX + prayer)
                .putExtra(EXTRA_PRAYER, prayer);
        return PendingIntent.getBroadcast(context, prayerIndex(prayer), alarm, flags);
    }

    private static boolean isKnownPrayer(String prayer) {
        return prayer != null && prayerIndex(prayer) >= 0;
    }

    private static int prayerIndex(String prayer) {
        if (prayer == null) return -1;
        for (int i = 0; i < PRAYERS.length; i++) if (PRAYERS[i].equals(prayer)) return i;
        return -1;
    }
}
