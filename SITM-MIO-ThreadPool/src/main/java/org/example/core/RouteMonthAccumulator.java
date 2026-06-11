package org.example.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.logic.GeoUtils;
import org.example.model.Datagram;

/**
 * Single-threaded accumulator used inside each chunk task.
 *
 * <p>The {@code calculationStartOffset} parameter ensures that datagrams read
 * from the overlap region (before the chunk's logical start) are used as
 * context (previous bus position) but are NOT counted in the speed tallies.
 * This prevents any measurement from being counted more than once across chunks.
 *
 * <p>Business rules are identical to the distributed SpeedCalculator:
 * <ul>
 *   <li>Only consecutive datagrams of the same bus are compared.</li>
 *   <li>Speed is calculated only if both datagrams share the same lineId.</li>
 *   <li>Invalid coordinates are skipped.</li>
 *   <li>Time deltas &lt;= 0 or &gt;= 300 s are rejected.</li>
 *   <li>Speeds &lt;= 0 or &gt;= 120 km/h are rejected.</li>
 *   <li>Results are grouped by (lineId, YearMonth of current datagram).</li>
 * </ul>
 */
public class RouteMonthAccumulator {

    private static final long   MAX_DELTA_SECONDS = 300L;
    private static final double MAX_SPEED_KMH     = 120.0;

    private final long calculationStartOffset;

    private final Map<Integer, Datagram>     lastByBus = new HashMap<>();
    private final Map<RouteMonthKey, SpeedStats> stats = new HashMap<>();

    private int processedCount = 0;
    private int speedCount     = 0;

    /**
     * @param calculationStartOffset byte offset below which datagrams are
     *                               used for context but not counted
     */
    public RouteMonthAccumulator(long calculationStartOffset) {
        this.calculationStartOffset = calculationStartOffset;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Feed one datagram into the accumulator. */
    public void feed(Datagram current) {
        boolean countable = current.getSourceOffset() >= calculationStartOffset;
        if (countable) {
            processedCount++;
        }

        if (!current.hasValidCoordinates()) {
            lastByBus.put(current.getBusId(), current);
            return;
        }

        Datagram previous = lastByBus.get(current.getBusId());

        if (previous != null
                && previous.hasValidCoordinates()
                && previous.getLineId() == current.getLineId()
                && countable) {
            tryCalculateSpeed(previous, current);
        }

        lastByBus.put(current.getBusId(), current);
    }
    
    public int getProcessedCount()  { return processedCount; }
    public int getSpeedCount()      { return speedCount; }
    public int getRouteMonthCount() { return stats.size(); }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void tryCalculateSpeed(Datagram previous, Datagram current) {
    // Reemplaza Duration.between por operaciones primitivas de segundos si es posible,
    // o mantén Duration si no es el cuello de botella principal tras quitar el parseo.
    long seconds = java.time.Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();

    if (seconds <= 0 || seconds >= MAX_DELTA_SECONDS) {
        return;
    }

    // Usar la aproximación plana adaptada a Cali
    double distanceMeters = GeoUtils.fastDistance(
            previous.getLatitud(), previous.getLongitud(),
            current.getLatitud(),  current.getLongitud());

    double speedKmh = (distanceMeters / seconds) * 3.6;

    if (speedKmh <= 0 || speedKmh >= MAX_SPEED_KMH) {
        return;
    }

    // USAR EL ENTERO: Cero Strings creados aquí
    RouteMonthKey key = new RouteMonthKey(current.getLineId(), current.getYearMonthCode());
    stats.computeIfAbsent(key, ignored -> new SpeedStats()).add(speedKmh);
    speedCount++;
}

// Modificar getResults para construir el String del mes SOLO al final del proceso
public List<RouteMonthResult> getResults() {
    List<RouteMonthResult> results = new ArrayList<>(stats.size());
    for (Map.Entry<RouteMonthKey, SpeedStats> e : stats.entrySet()) {
        RouteMonthKey k = e.getKey();
        SpeedStats    s = e.getValue();
        
        // Reconstruir formato "YYYY-MM" a partir del entero (ej: 202308 -> "2023-08")
        String monthStr = String.format("%d-%02d", k.monthCode / 100, k.monthCode % 100);
        results.add(new RouteMonthResult(k.lineId, monthStr, s.speedSum, s.count));
    }
    return results;
}

// Modificar la Key interna para que use tipos primitivos
private static final class RouteMonthKey {
    final int lineId;
    final int monthCode; // Cambiado de String a int

    RouteMonthKey(int lineId, int monthCode) {
        this.lineId = lineId;
        this.monthCode  = monthCode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteMonthKey)) return false;
        RouteMonthKey that = (RouteMonthKey) o;
        return lineId == that.lineId && monthCode == that.monthCode;
    }

    @Override
    public int hashCode() { 
        // Hash numérico simple y rápido sin arrays intermedios
        return 31 * lineId + monthCode; 
    }
}

    private static final class SpeedStats {
        double speedSum = 0.0;
        int    count    = 0;

        void add(double speed) { speedSum += speed; count++; }
    }
}
