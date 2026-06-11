package org.example.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;

import org.example.logic.GeoUtils;
import org.example.model.Datagram;

public class SpeedCalculator implements Runnable {

    // Ajustables para pruebas: limite de velocidad maxima y ventana maxima entre datagramas.
    private static final long MAX_SECONDS_BETWEEN_POSITIONS = 300;
    private static final double MAX_VALID_SPEED_KMH = 120.0;
    private static final double MAX_VALID_SPEED_MPS = MAX_VALID_SPEED_KMH / 3.6;

    private final BlockingQueue<Datagram> queue;
    private final long calculationStartOffset;

    public int processed = 0;
    public int invalidCoords = 0;
    public int speedCalculated = 0;

    private final Map<RouteMonthKey, SpeedStats> routeMonthSpeedMap = new HashMap<>();
    private final Map<Integer, Datagram> lastPositionByBus = new HashMap<>();

    public SpeedCalculator(BlockingQueue<Datagram> queue, long calculationStartOffset) {
        this.queue = queue;
        this.calculationStartOffset = calculationStartOffset;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Datagram datagram = queue.take();

                if (datagram.isPoisonPill()) {
                    break;
                }

                processed++;

                if (!datagram.hasValidCoordinates()) {
                    invalidCoords++;
                    continue;
                }

                Datagram last = lastPositionByBus.put(datagram.getBusId(), datagram);

                if (last != null && datagram.getSourceOffset() >= calculationStartOffset) {
                    if (datagram.getTripId() == last.getTripId() && datagram.getLineId() == last.getLineId()) {
                        long seconds = datagram.getTimestampSeconds() - last.getTimestampSeconds();

                        if (seconds > 0 && seconds <= MAX_SECONDS_BETWEEN_POSITIONS) {
                            double maxAllowedMeters = MAX_VALID_SPEED_MPS * seconds;
                            double lowerBoundMeters = GeoUtils.cheapLowerBoundMeters(
                                    last.getLatRad(), last.getLonRad(), last.getCosLat(),
                                    datagram.getLatRad(), datagram.getLonRad(), datagram.getCosLat()
                            );
                            if (lowerBoundMeters > maxAllowedMeters) {
                                continue;
                            }

                            // El odometro no se usa porque no es confiable en buses antiguos del MIO.
                            // El calculo oficial usa unicamente distancia GPS Haversine y diferencia temporal.
                            double distance = GeoUtils.haversineRadians(
                                    last.getLatRad(), last.getLonRad(), last.getCosLat(),
                                    datagram.getLatRad(), datagram.getLonRad(), datagram.getCosLat()
                            );
                            double speedKmh = (distance / seconds) * 3.6;

                            if (speedKmh > 0 && speedKmh <= MAX_VALID_SPEED_KMH) {
                                RouteMonthKey key = new RouteMonthKey(datagram.getLineId(), datagram.getYearMonth());
                                SpeedStats stats = routeMonthSpeedMap.computeIfAbsent(key, k -> new SpeedStats());
                                stats.addSpeed(speedKmh);
                                speedCalculated++;
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<RouteMonthKey, SpeedStats> getRouteMonthSpeedMap() {
        return routeMonthSpeedMap;
    }

    public static class RouteMonthKey {
        private final int lineId;
        private final int month;

        public RouteMonthKey(int lineId, int month) {
            this.lineId = lineId;
            this.month = month;
        }

        public int getLineId() {
            return lineId;
        }

        public int getMonth() {
            return month;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteMonthKey)) return false;
            RouteMonthKey that = (RouteMonthKey) o;
            return lineId == that.lineId && month == that.month;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineId, month);
        }
    }

    public static class SpeedStats {
        private double totalSpeed = 0;
        private int count = 0;

        public void addSpeed(double speed) {
            totalSpeed += speed;
            count++;
        }

        public double getTotalSpeed() {
            return totalSpeed;
        }

        public int getCount() {
            return count;
        }
    }
}
