package com.masjidph.navigator;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Strict, deterministic time conversion for the opt-in local prayer reminders. */
public final class PrayerScheduleTimes {
    private PrayerScheduleTimes() { }

    /**
     * Parses a prayer date and 24-hour clock time in the supplied IANA time zone.
     * Rejects invalid dates, invalid hours/minutes, and trailing text.
     */
    public static long parsePrayerTimeMillis(String date, String time, String timeZoneId)
            throws ParseException {
        if (date == null || time == null || timeZoneId == null
                || !date.matches("^\\d{2}-\\d{2}-\\d{4}$")
                || !time.matches("^\\d{2}:\\d{2}$")
                || timeZoneId.trim().isEmpty()) {
            throw new ParseException("Invalid prayer date, time, or time zone", 0);
        }

        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US);
        format.setLenient(false);
        TimeZone zone = TimeZone.getTimeZone(timeZoneId);
        if ("GMT".equals(zone.getID()) && !"GMT".equals(timeZoneId)) {
            // TimeZone.getTimeZone silently falls back to GMT for unknown IDs.
            throw new ParseException("Unknown time zone: " + timeZoneId, 0);
        }
        format.setTimeZone(zone);

        String input = date + " " + time;
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(input, position);
        if (parsed == null || position.getIndex() != input.length()) {
            int error = position.getErrorIndex() >= 0 ? position.getErrorIndex() : position.getIndex();
            throw new ParseException("Invalid prayer time: " + input, error);
        }
        return parsed.getTime();
    }

    /**
     * Parses an entire prayer schedule before callers replace any active alarms.
     * Input times must be 24-hour HH:mm values in the requested local time zone.
     */
    public static long[] parsePrayerTimesMillis(String date, String[] times, String timeZoneId)
            throws ParseException {
        if (times == null || times.length == 0) {
            throw new ParseException("Prayer schedule is empty", 0);
        }
        long[] parsed = new long[times.length];
        for (int i = 0; i < times.length; i++) {
            try {
                parsed[i] = parsePrayerTimeMillis(date, times[i], timeZoneId);
            } catch (ParseException invalid) {
                ParseException contextual = new ParseException(
                        "Invalid prayer time at index " + i + ": " + invalid.getMessage(),
                        invalid.getErrorOffset());
                contextual.initCause(invalid);
                throw contextual;
            }
        }
        return parsed;
    }

    /** A prayer alarm is eligible only when it is strictly in the future. */
    public static boolean isFutureTime(long prayerTimeMillis, long nowMillis) {
        return prayerTimeMillis > nowMillis;
    }
}
