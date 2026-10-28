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

    /** A prayer alarm is eligible only when it is strictly in the future. */
    public static boolean isFutureTime(long prayerTimeMillis, long nowMillis) {
        return prayerTimeMillis > nowMillis;
    }
}
