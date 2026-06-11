package org.example.report;

import org.example.core.RouteMonthResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Writes the route × month speed matrix to a CSV file.
 *
 * <p>Output format:
 * <pre>
 * route_id,route_short_name,route_description,2018-06,2018-07,...
 * 101,"T31","Terminal - Universidades",32.4512,28.1034,...
 * </pre>
 *
 * <ul>
 *   <li>Every active route is included (blank cell if no data for that month).</li>
 *   <li>Averages are computed as {@code speedSum / count} — never average of averages.</li>
 *   <li>Velocities are in km/h, formatted with 4 decimal places.</li>
 * </ul>
 */
public class RouteMonthCsvWriter {

    /**
     * Writes the matrix to {@code outputPath}.
     *
     * @param results     aggregated route-month data
     * @param activeLines reference list of all active routes
     * @param outputPath  destination file path
     * @return absolute path of the written file
     * @throws IOException on any I/O error
     */
    public Path writeMatrix(
            List<RouteMonthResult> results,
            List<ActiveLine>       activeLines,
            String                 outputPath) throws IOException {

        // Build aggregated totals (safe merge: sum speedSum and count)
        Map<RouteMonthKey, Totals> totals = aggregate(results);

        // Build ordered map of active lines (dedup by lineId)
        Map<Integer, ActiveLine> linesMap = buildLinesMap(activeLines);

        // Collect all months that appear in the data for active routes
        Set<String> months = collectMonths(totals, linesMap.keySet());

        Path output = Paths.get(outputPath);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {

            // Header row
            writer.write("route_id,route_short_name,route_description");
            for (String month : months) {
                writer.write(",");
                writer.write(month);
            }
            writer.newLine();

            // One row per active route
            for (ActiveLine line : linesMap.values()) {
                writer.write(Integer.toString(line.getLineId()));
                writer.write(",");
                writer.write(quote(line.getShortName()));
                writer.write(",");
                writer.write(quote(line.getDescription()));

                for (String month : months) {
                    Totals t = totals.get(new RouteMonthKey(line.getLineId(), month));
                    writer.write(",");
                    if (t != null && t.count > 0) {
                        writer.write(String.format(Locale.US, "%.4f", t.speedSum / t.count));
                    }
                }
                writer.newLine();
            }
        }

        return output.toAbsolutePath();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Map<RouteMonthKey, Totals> aggregate(List<RouteMonthResult> results) {
        Map<RouteMonthKey, Totals> totals = new LinkedHashMap<>();
        for (RouteMonthResult r : results) {
            RouteMonthKey key = new RouteMonthKey(r.getLineId(), r.getMonth());
            totals.computeIfAbsent(key, ignored -> new Totals()).add(r.getSpeedSum(), r.getCount());
        }
        return totals;
    }

    private Map<Integer, ActiveLine> buildLinesMap(List<ActiveLine> activeLines) {
        Map<Integer, ActiveLine> map  = new LinkedHashMap<>();
        Set<Integer>             seen = new HashSet<>();
        for (ActiveLine al : activeLines) {
            if (seen.add(al.getLineId())) {
                map.put(al.getLineId(), al);
            }
        }
        return map;
    }

    private Set<String> collectMonths(Map<RouteMonthKey, Totals> totals, Set<Integer> activeIds) {
        Set<String> months = new TreeSet<>();
        for (RouteMonthKey key : totals.keySet()) {
            if (activeIds.contains(key.lineId)) {
                months.add(key.month);
            }
        }
        return months;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
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
        public int hashCode() { return Objects.hash(lineId, month); }
    }

    private static final class Totals {
        double speedSum = 0.0;
        int    count    = 0;

        void add(double speedSum, int count) {
            this.speedSum += speedSum;
            this.count    += count;
        }
    }
}
