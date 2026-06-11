package org.example.core;

import java.util.List;

public class ChunkResult {
    private final String workerName;
    private final long executionTime;
    private final int processedCount;
    private final int speedCount;
    private final List<RouteMonthResult> routeMonthResults;

    public ChunkResult(
            String workerName,
            long executionTime,
            int processedCount,
            int speedCount,
            List<RouteMonthResult> routeMonthResults
    ) {
        this.workerName = workerName;
        this.executionTime = executionTime;
        this.processedCount = processedCount;
        this.speedCount = speedCount;
        this.routeMonthResults = routeMonthResults;
    }

    public String getWorkerName() {
        return workerName;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getSpeedCount() {
        return speedCount;
    }

    public List<RouteMonthResult> getRouteMonthResults() {
        return routeMonthResults;
    }
}
