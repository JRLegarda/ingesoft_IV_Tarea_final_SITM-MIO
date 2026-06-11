package org.example.core;

/**
 * Immutable value object that carries aggregated speed data for one
 * (route, month) pair.  Stores the raw sum and count so that results
 * from multiple sources can be merged without averaging averages.
 */
public class RouteMonthResult {

    private final int lineId;
    private final String month;
    private final double speedSum;
    private final int count;

    public RouteMonthResult(int lineId, String month, double speedSum, int count) {
        this.lineId    = lineId;
        this.month     = month;
        this.speedSum  = speedSum;
        this.count     = count;
    }

    public int    getLineId()   { return lineId; }
    public String getMonth()    { return month; }
    public double getSpeedSum() { return speedSum; }
    public int    getCount()    { return count; }

    /** Returns the true average, or 0 if there are no measurements. */
    public double getAverageSpeed() {
        return count > 0 ? speedSum / count : 0.0;
    }
}
