package com.masjidph.navigator;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes the next day's prayer schedule using the last location explicitly
 * requested by the user. Uses inexact alarms so the app does not require the
 * restricted exact-alarm permission.
 */
public final class PrayerScheduleWorker extends Worker {
    public static final String PREFS_NAME = "prayer_reminders";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    private static final String UNIQUE_WORK = "daily_prayer_schedule";
    private static final String ZONE = "Asia/Manila";
    private static final String[] PRAYERS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    public PrayerScheduleWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueueNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PrayerScheduleWorker.class)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK);
        PrayerReminderReceiver.cancelAll(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return Result.success();

        String latText = prefs.getString(KEY_LATITUDE, null);
        String lonText = prefs.getString(KEY_LONGITUDE, null);
        if (latText == null || lonText == null) {
            scheduleNext(context);
            return Result.success();
        }

        try {
            double latitude = Double.parseDouble(latText);
            double longitude = Double.parseDouble(lonText);
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                scheduleNext(context);
                return Result.failure();
            }
            refreshPrayerAlarms(context, latitude, longitude);
            scheduleNext(context);
            return Result.success();
        } catch (Exception exception) {
            // Retry transient network/API errors; cap retries so a persistent
            // outage still leaves a future daily refresh queued.
            if (getRunAttemptCount() >= 4) {
                scheduleNext(context);
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private void refreshPrayerAlarms(Context context, double latitude, double longitude) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone(ZONE));
        String date = dateFormat.format(new Date());
        String lat = String.format(Locale.US, "%.6f", latitude);
        String lon = String.format(Locale.US, "%.6f", longitude);
        URL url = new URL("https://api.aladhan.com/v1/timings/" + date
                + "?latitude=" + lat + "&longitude=" + lon
                + "&method=3&school=0&timezonestring=Asia%2FManila");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new java.io.IOException("Prayer service returned HTTP " + responseCode);
            }
            StringBuilder response = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONObject json = new JSONObject(response.toString());
            if (json.optInt("code") != 200 || !json.has("data")) {
                throw new java.io.IOException("Prayer service returned an invalid response");
            }
            JSONObject timings = json.getJSONObject("data").getJSONObject("timings");
            for (String prayer : PRAYERS) {
                String raw = timings.optString(prayer, "").trim();
                int space = raw.indexOf(' ');
                String time = space > 0 ? raw.substring(0, space) : raw;
                if (!time.matches("^\\d{2}:\\d{2}$")) {
                    // Explicitly cancel this prayer's previous alarm when an API
                    // response is malformed; do not leave a stale reminder behind.
                    PrayerReminderReceiver.schedule(context, prayer, 0L);
                    throw new ParseException("Invalid time returned for " + prayer, 0);
                }
                long fireAt = PrayerScheduleTimes.parsePrayerTimeMillis(date, time, ZONE);
                // schedule() replaces future alarms and cancels times that have
                // passed, including after the saved location or device clock changes.
                PrayerReminderReceiver.schedule(context, prayer, fireAt);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void scheduleNext(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(ZONE));
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(ZONE));
        long delay = Math.max(TimeUnit.MINUTES.toMillis(1), Duration.between(now, nextMidnight).toMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PrayerScheduleWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}
