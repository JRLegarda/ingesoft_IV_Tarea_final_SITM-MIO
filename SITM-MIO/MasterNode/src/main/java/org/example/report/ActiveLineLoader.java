package org.example.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ActiveLineLoader {

    public List<ActiveLine> load(String configuredPath) {
        try {
            List<String> rows = readRows(configuredPath);
            return parseRows(rows);
        } catch (Exception e) {
            System.err.println("[Report] No se pudieron cargar rutas activas: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> readRows(String configuredPath) throws IOException {
        for (Path candidate : candidates(configuredPath)) {
            if (Files.isRegularFile(candidate)) {
                System.out.println("[Report] Rutas activas cargadas desde: " + candidate.toAbsolutePath());
                return Files.readAllLines(candidate, StandardCharsets.UTF_8);
            }
        }

        InputStream is = ActiveLineLoader.class.getClassLoader().getResourceAsStream(configuredPath);
        if (is != null) {
            System.out.println("[Report] Rutas activas cargadas desde classpath: " + configuredPath);
            List<String> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rows.add(line);
                }
            }
            return rows;
        }

        throw new IOException("archivo no encontrado: " + configuredPath);
    }

    private List<Path> candidates(String configuredPath) {
        List<Path> paths = new ArrayList<>();
        Path configured = Paths.get(configuredPath);
        paths.add(configured);
        if (!configured.isAbsolute()) {
            paths.add(Paths.get("..", configuredPath));
            paths.add(Paths.get("..", "..", configuredPath));
            paths.add(Paths.get("src", "main", "resources", configuredPath));
            paths.add(Paths.get("/opt/sitm-mio", configuredPath));
        }
        return paths;
    }

    private List<ActiveLine> parseRows(List<String> rows) {
        List<ActiveLine> lines = new ArrayList<>();
        boolean first = true;
        for (String row : rows) {
            if (first) {
                first = false;
                continue;
            }
            if (row == null || row.trim().isEmpty()) {
                continue;
            }

            List<String> cols = splitCsv(row);
            if (cols.size() < 4) {
                continue;
            }

            try {
                lines.add(new ActiveLine(
                        Integer.parseInt(cols.get(0).trim()),
                        cols.get(2).trim(),
                        cols.get(3).trim()
                ));
            } catch (NumberFormatException ignored) {
                // Ignore malformed rows and keep the report usable.
            }
        }
        return lines;
    }

    private List<String> splitCsv(String row) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(current.toString());
        return cols;
    }
}
