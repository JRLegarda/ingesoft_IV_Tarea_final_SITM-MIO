package org.example.logic;

public class GeoUtils {
    private static final double R = 6371e3;

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        return haversineRadians(lat1Rad, lon1Rad, Math.cos(lat1Rad), lat2Rad, lon2Rad, Math.cos(lat2Rad));
    }

    public static double haversineRadians(
            double lat1Rad,
            double lon1Rad,
            double cosLat1,
            double lat2Rad,
            double lon2Rad,
            double cosLat2
    ) {
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double a = sinLat * sinLat + cosLat1 * cosLat2 * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public static double cheapLowerBoundMeters(
            double lat1Rad,
            double lon1Rad,
            double cosLat1,
            double lat2Rad,
            double lon2Rad,
            double cosLat2
    ) {
        double latMeters = Math.abs(lat2Rad - lat1Rad) * R;
        double minCosLat = Math.min(Math.abs(cosLat1), Math.abs(cosLat2));
        double lonMeters = Math.abs(lon2Rad - lon1Rad) * R * minCosLat;
        return Math.max(latMeters, lonMeters);
    }
}
