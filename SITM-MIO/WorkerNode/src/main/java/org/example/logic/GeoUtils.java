package org.example.logic;

public class GeoUtils {
    private static final double CALI_AVERAGE_LATITUDE = 3.45;
    private static final double METERS_PER_DEGREE_LAT = 111320.0;
    private static final double METERS_PER_DEGREE_LON =
            METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(CALI_AVERAGE_LATITUDE));

    public static double euclideanDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double x1 = lon1 * METERS_PER_DEGREE_LON;
        double y1 = lat1 * METERS_PER_DEGREE_LAT;
        double x2 = lon2 * METERS_PER_DEGREE_LON;
        double y2 = lat2 * METERS_PER_DEGREE_LAT;

        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double euclideanLowerBoundMeters(double lat1, double lon1, double lat2, double lon2) {
        double latMeters = Math.abs(lat2 - lat1) * METERS_PER_DEGREE_LAT;
        double lonMeters = Math.abs(lon2 - lon1) * METERS_PER_DEGREE_LON;
        return Math.max(latMeters, lonMeters);
    }
}
