package org.example.core;

import org.example.logic.GeoUtils;
import org.example.model.Datagram;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sequential (single-threaded) accumulator that computes average speed per
 * route per month from a stream of datagrams.
 *
 * <p>Business rules (same as the distributed SpeedCalculator):
 * <ul>
 *   <li>Compare only consecutive datagrams of the same bus.</li>
 *   <li>Calculate speed only if the previous datagram has the same lineId.</li>
 *   <li>Ignore datagrams with invalid coordinates.</li>
 *   <li>Ignore pairs with a time delta &lt;= 0 or &gt;= 300 seconds.</li>
 *   <li>Ignore computed speeds &lt;= 0 or &gt;= 120 km/h.</li>
 *   <li>Group results by lineId and YearMonth of the current datagram.</li>
 *   <li>Store speedSum and count; compute average as speedSum / count.</li>
 * </ul>
 */
public class RouteMonthAccumulator {

    private static final long   MAX_DELTA_SECONDS   = 300L;
    private static final double MAX_SPEED_KMH       = 120.0;

    /** Last valid datagram seen for each bus. */
    private final Map<Integer, Datagram> lastByBus = new HashMap<>();

    /** Accumulated speed data per (lineId, month) key. */
    private final Map<RouteMonthKey, SpeedStats> stats = new HashMap<>();

    private int processedCount = 0;
    private int speedCount     = 0;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Feeds one datagram into the accumulator.
     *
     * @param current the datagram to process
     */
    public void feed(Datagram current) {
        processedCount++;

        if (!current.hasValidCoordinates()) {
            // Still update last-position so the bus is tracked
            lastByBus.put(current.getBusId(), current);
            return;
        }

        Datagram previous = lastByBus.get(current.getBusId());

        if (previous != null
                && previous.hasValidCoordinates()
                && previous.getLineId() == current.getLineId()) {
            tryCalculateSpeed(previous, current);
        }

        lastByBus.put(current.getBusId(), current);
    }

    /**
     * Returns all accumulated route-month results as an immutable snapshot.
     */
    public List<RouteMonthResult> getResults() {
        List<RouteMonthResult> results = new ArrayList<>(stats.size());
        for (Map.Entry<RouteMonthKey, SpeedStats> entry : stats.entrySet()) {
            RouteMonthKey key = entry.getKey();
            SpeedStats    s   = entry.getValue();
            results.add(new RouteMonthResult(key.lineId, key.month, s.speedSum, s.count));
        }
        return results;
    }

    public int getProcessedCount() { return processedCount; }
    public int getSpeedCount()     { return speedCount; }
    public int getRouteMonthCount(){ return stats.size(); }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void tryCalculateSpeed(Datagram previous, Datagram current) {
        Duration delta   = Duration.between(previous.getTimestamp(), current.getTimestamp());
        long     seconds = delta.getSeconds();

        if (seconds <= 0 || seconds >= MAX_DELTA_SECONDS) {
            return;
        }

        double distanceMeters = GeoUtils.haversine(
                previous.getLatitud(), previous.getLongitud(),
                current.getLatitud(),  current.getLongitud());

        double speedKmh = (distanceMeters / seconds) * 3.6;

        if (speedKmh <= 0 || speedKmh >= MAX_SPEED_KMH) {
            return;
        }

        String month = YearMonth.from(current.getTimestamp()).toString();
        RouteMonthKey key = new RouteMonthKey(current.getLineId(), month);
        stats.computeIfAbsent(key, ignored -> new SpeedStats()).add(speedKmh);
        speedCount++;
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    private static final class RouteMonthKey {
        final int    lineId;
        final String month;

        RouteMonthKey(int lineId, String month) {
            this.lineId = lineId;
            this.month  = month;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RouteMonthKey)) return false;
            RouteMonthKey that = (RouteMonthKey) o;
            return lineId == that.lineId && Objects.equals(month, that.month);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineId, month);
        }
    }

    private static final class SpeedStats {
        double speedSum = 0.0;
        int    count    = 0;

        void add(double speed) {
            speedSum += speed;
            count++;
        }
    }
}
