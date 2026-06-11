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
 * <p>Averages are computed as {@code speedSum / count} after merging all
 * chunk results — never average of averages.
 */
public class RouteMonthCsvWriter {

    public Path writeMatrix(
            List<RouteMonthResult> results,
            List<ActiveLine>       activeLines,
            String                 outputPath) throws IOException {

        Map<RouteMonthKey, Totals> totals   = aggregate(results);
        Map<Integer, ActiveLine>   linesMap = buildLinesMap(activeLines);
        Set<String>                months   = collectMonths(totals, linesMap.keySet());

        Path output = Paths.get(outputPath);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {

            writer.write("route_id,route_short_name,route_description");
            for (String month : months) { writer.write(","); writer.write(month); }
            writer.newLine();

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

    private Map<RouteMonthKey, Totals> aggregate(List<RouteMonthResult> results) {
        Map<RouteMonthKey, Totals> totals = new LinkedHashMap<>();
        for (RouteMonthResult r : results) {
            totals.computeIfAbsent(new RouteMonthKey(r.getLineId(), r.getMonth()),
                    ignored -> new Totals())
                  .add(r.getSpeedSum(), r.getCount());
        }
        return totals;
    }

    private Map<Integer, ActiveLine> buildLinesMap(List<ActiveLine> activeLines) {
        Map<Integer, ActiveLine> map  = new LinkedHashMap<>();
        Set<Integer>             seen = new HashSet<>();
        for (ActiveLine al : activeLines) {
            if (seen.add(al.getLineId())) map.put(al.getLineId(), al);
        }
        return map;
    }

    private Set<String> collectMonths(Map<RouteMonthKey, Totals> totals, Set<Integer> activeIds) {
        Set<String> months = new TreeSet<>();
        for (RouteMonthKey key : totals.keySet()) {
            if (activeIds.contains(key.lineId)) months.add(key.month);
        }
        return months;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    // -----------------------------------------------------------------------

    private static final class RouteMonthKey {
        final int lineId; final String month;
        RouteMonthKey(int lineId, String month) { this.lineId = lineId; this.month = month; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof RouteMonthKey)) return false;
            RouteMonthKey that = (RouteMonthKey) o;
            return lineId == that.lineId && Objects.equals(month, that.month);
        }
        @Override public int hashCode() { return Objects.hash(lineId, month); }
    }

    private static final class Totals {
        double speedSum = 0.0; int count = 0;
        void add(double s, int c) { speedSum += s; count += c; }
    }
}
