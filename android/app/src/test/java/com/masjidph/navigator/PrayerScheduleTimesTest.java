package com.masjidph.navigator;

import org.junit.Test;

import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PrayerScheduleTimesTest {
    @Test
    public void parsesPrayerTimeInManilaTimeZone() throws Exception {
        long actual = PrayerScheduleTimes.parsePrayerTimeMillis(
                "29-10-2026", "05:30", "Asia/Manila");
        long expected = ZonedDateTime.of(2026, 10, 29, 5, 30, 0, 0,
                ZoneId.of("Asia/Manila")).toInstant().toEpochMilli();
        assertEquals(expected, actual);
    }

    @Test
    public void rejectsImpossibleDatesAndTimes() throws Exception {
        assertInvalid("31-02-2026", "05:30", "Asia/Manila");
        assertInvalid("29-10-2026", "24:00", "Asia/Manila");
        assertInvalid("29-10-2026", "05:75", "Asia/Manila");
        assertInvalid("29-10-2026", "05:30garbage", "Asia/Manila");
        assertInvalid("29/10/2026", "05:30", "Asia/Manila");
    }

    @Test
    public void rejectsUnknownTimeZone() throws Exception {
        assertInvalid("29-10-2026", "05:30", "Unknown/Nowhere");
    }

    @Test
    public void onlySchedulesStrictlyFuturePrayerTimes() {
        assertTrue(PrayerScheduleTimes.isFutureTime(101L, 100L));
        assertFalse(PrayerScheduleTimes.isFutureTime(100L, 100L));
        assertFalse(PrayerScheduleTimes.isFutureTime(99L, 100L));
    }

    private void assertInvalid(String date, String time, String zone) {
        try {
            PrayerScheduleTimes.parsePrayerTimeMillis(date, time, zone);
            fail("Expected invalid prayer time to be rejected");
        } catch (ParseException expected) {
            // Expected: invalid API timings must not create alarms.
        }
    }
}
