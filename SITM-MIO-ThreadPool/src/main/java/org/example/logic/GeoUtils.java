package org.example.logic;

/** Geographic utility functions. */
public class GeoUtils {

    private static final double R = 6_371_000.0;

    private GeoUtils() {}

    /**
     * Great-circle distance between two points (Haversine formula).
     *
     * @return distance in metres
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Distancia aproximada por superficie plana (Ideal para perímetros urbanos).
     * Reduce drásticamente las operaciones trigonométricas costosas.
     */
    public static double fastDistance(double lat1, double lon1, double lat2, double lon2) {
        double latMid = Math.toRadians((lat1 + lat2) / 2.0);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double x = dLon * Math.cos(latMid);
        double y = dLat;
        return R * Math.sqrt(x * x + y * y);
    }
}
