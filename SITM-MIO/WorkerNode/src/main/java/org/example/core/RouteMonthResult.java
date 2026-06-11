package org.example.core;

public class RouteMonthResult {
    private final int lineId;
    private final String month;
    private final double speedSum;
    private final int count;

    public RouteMonthResult(int lineId, String month, double speedSum, int count) {
        this.lineId = lineId;
        this.month = month;
        this.speedSum = speedSum;
        this.count = count;
    }

    public int getLineId() {
        return lineId;
    }

    public String getMonth() {
        return month;
    }

    public double getSpeedSum() {
        return speedSum;
    }

    public int getCount() {
        return count;
    }
}
