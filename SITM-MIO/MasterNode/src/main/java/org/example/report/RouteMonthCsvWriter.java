package org.example.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import Demo.RouteMonthSpeed;
import Demo.TaskResult;

public class RouteMonthCsvWriter {

    public Path writeMatrix(List<TaskResult> results, List<ActiveLine> activeLines, String outputPath) throws IOException {
        Map<RouteMonthKey, Totals> totals = aggregate(results);
        Map<Integer, ActiveLine> lines = linesForReport(activeLines);
        Set<String> months = collectMonths(totals, lines.keySet());

        Path output = Paths.get(outputPath);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("route_id,route_short_name,route_description");
            for (String month : months) {
                writer.write(",");
                writer.write(month);
            }
            writer.newLine();

            for (ActiveLine line : lines.values()) {
                writer.write(Integer.toString(line.getLineId()));
                writer.write(",");
                writer.write(quote(line.getShortName()));
                writer.write(",");
                writer.write(quote(line.getDescription()));

                // Búsqueda instantánea O(1) en el mapa estructurado
                for (String month : months) {
                    writer.write(",");
                    RouteMonthKey key = new RouteMonthKey(line.getLineId(), month);
                    Totals cellData = totals.get(key);
                    
                    if (cellData != null && cellData.count > 0) {
                        double avg = cellData.speedSum / cellData.count;
                        writer.write(String.format(Locale.US, "%.2f", avg));
                    } else {
                        writer.write("");
                    }
                }
                writer.newLine();
            }
        }
        return output;
    }

    private Map<RouteMonthKey, Totals> aggregate(List<TaskResult> results) {
        Map<RouteMonthKey, Totals> target = new HashMap<>();
        for (TaskResult result : results) {
            if (result.routeMonthSpeeds == null) continue;
            for (RouteMonthSpeed rms : result.routeMonthSpeeds) {
                RouteMonthKey key = new RouteMonthKey(rms.lineId, rms.month);
                // Si la clave no existe, añade un objeto Totals nuevo y acumula
                target.computeIfAbsent(key, k -> new Totals()).add(rms.speedSum, rms.count);
            }
        }
        return target;
    }

    private Set<String> collectMonths(Map<RouteMonthKey, Totals> totals, Set<Integer> activeLineIds) {
        Set<String> months = new TreeSet<>();
        for (RouteMonthKey key : totals.keySet()) {
            if (activeLineIds.contains(key.lineId)) {
                months.add(key.month);
            }
        }
        return months;
    }

    private Map<Integer, ActiveLine> linesForReport(List<ActiveLine> activeLines) {
        Map<Integer, ActiveLine> lines = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (ActiveLine activeLine : activeLines) {
            if (seen.add(activeLine.getLineId())) {
                lines.put(activeLine.getLineId(), activeLine);
            }
        }
        return lines;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static class RouteMonthKey {
        private final int lineId;
        private final String month;

        private RouteMonthKey(int lineId, String month) {
            this.lineId = lineId;
            this.month = month;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteMonthKey)) return false;
            RouteMonthKey that = (RouteMonthKey) o;
            return lineId == that.lineId && java.util.Objects.equals(month, that.month);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(lineId, month);
        }
    }

    private static class Totals {
        private double speedSum;
        private int count;

        private void add(double speedSum, int count) {
            this.speedSum += speedSum;
            this.count += count;
        }
    }
}
