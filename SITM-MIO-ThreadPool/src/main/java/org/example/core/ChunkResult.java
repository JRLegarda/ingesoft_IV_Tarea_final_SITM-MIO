package org.example.core;

import java.util.List;

/** Results produced by processing one byte-range chunk. */
public class ChunkResult {

    private final String                  label;
    private final long                    executionTimeMs;
    private final int                     processedCount;
    private final int                     speedCount;
    private final List<RouteMonthResult>  routeMonthResults;

    public ChunkResult(
            String label,
            long executionTimeMs,
            int processedCount,
            int speedCount,
            List<RouteMonthResult> routeMonthResults) {

        this.label             = label;
        this.executionTimeMs   = executionTimeMs;
        this.processedCount    = processedCount;
        this.speedCount        = speedCount;
        this.routeMonthResults = routeMonthResults;
    }

    public String                 getLabel()             { return label; }
    public long                   getExecutionTimeMs()   { return executionTimeMs; }
    public int                    getProcessedCount()    { return processedCount; }
    public int                    getSpeedCount()        { return speedCount; }
    public List<RouteMonthResult> getRouteMonthResults() { return routeMonthResults; }
}
