package com.masjidph.navigator;

/**
 * Small, deterministic geographic calculations shared by the native UI and unit tests.
 */
public final class PrayerMath {
    private static final double KAABA_LATITUDE = 21.4225;
    private static final double KAABA_LONGITUDE = 39.8262;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

    private PrayerMath() { }

    /**
     * Returns the initial great-circle bearing from a point to the Kaaba,
     * measured clockwise from true north in degrees in [0, 360).
     */
    public static double calculateQiblaBearing(double latitude, double longitude) {
        requireCoordinates(latitude, longitude);
        double lat1 = latitude * DEGREES_TO_RADIANS;
        double lat2 = KAABA_LATITUDE * DEGREES_TO_RADIANS;
        double deltaLon = (KAABA_LONGITUDE - longitude) * DEGREES_TO_RADIANS;
        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    public static double normalizeDegrees(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Angle must be finite.");
        }
        return (value % 360.0 + 360.0) % 360.0;
    }

    private static void requireCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90.0 || latitude > 90.0
                || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Latitude or longitude is outside its valid range.");
        }
    }
}
