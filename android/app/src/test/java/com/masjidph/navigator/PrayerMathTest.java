package com.masjidph.navigator;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PrayerMathTest {
    @Test
    public void qiblaBearingFromManilaIsNorthwest() {
        double bearing = PrayerMath.calculateQiblaBearing(14.5995, 120.9842);
        assertEquals(290.0, bearing, 2.0);
        assertTrue(bearing >= 0.0 && bearing < 360.0);
    }

    @Test
    public void qiblaBearingFromSingaporeIsNorthwest() {
        double bearing = PrayerMath.calculateQiblaBearing(1.3521, 103.8198);
        assertEquals(293.0, bearing, 2.0);
        assertTrue(bearing >= 0.0 && bearing < 360.0);
    }

    @Test
    public void qiblaBearingAtKaabaIsZero() {
        assertEquals(0.0, PrayerMath.calculateQiblaBearing(21.4225, 39.8262), 1e-6);
    }

    @Test
    public void normalizesAnglesAcrossBothBoundaries() {
        assertEquals(350.0, PrayerMath.normalizeDegrees(-10.0), 1e-9);
        assertEquals(10.0, PrayerMath.normalizeDegrees(370.0), 1e-9);
        assertEquals(0.0, PrayerMath.normalizeDegrees(720.0), 1e-9);
    }

    @Test
    public void rejectsInvalidCoordinates() {
        assertInvalidCoordinates(91.0, 0.0);
        assertInvalidCoordinates(0.0, 181.0);
        assertInvalidCoordinates(Double.NaN, 0.0);
        assertInvalidCoordinates(0.0, Double.POSITIVE_INFINITY);
    }

    private void assertInvalidCoordinates(double latitude, double longitude) {
        try {
            PrayerMath.calculateQiblaBearing(latitude, longitude);
            fail("Expected invalid coordinates to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: malformed coordinates should never produce a compass direction.
        }
    }

    @Test
    public void rejectsNonFiniteAngles() {
        try {
            PrayerMath.normalizeDegrees(Double.NaN);
            fail("Expected non-finite angles to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
