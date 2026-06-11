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

/**
 * Loads the list of active MIO routes from {@code lines-241-ActiveGT.csv}.
 *
 * <p>Search order:
 * <ol>
 *   <li>Exact path provided by the caller.</li>
 *   <li>Project root and common relative paths.</li>
 *   <li>{@code src/main/resources/lines-241-ActiveGT.csv}.</li>
 *   <li>{@code /opt/sitm-mio/lines-241-ActiveGT.csv}.</li>
 *   <li>Java classpath resource.</li>
 * </ol>
 */
public class ActiveLineLoader {

    private static final String DEFAULT_FILENAME = "lines-241-ActiveGT.csv";

    public List<ActiveLine> load(String configuredPath) {
        String target = (configuredPath == null || configuredPath.isBlank())
                ? DEFAULT_FILENAME
                : configuredPath;
        try {
            List<String> rows = readRows(target);
            return parseRows(rows);
        } catch (Exception e) {
            System.err.println("[Report] No se pudieron cargar rutas activas: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // -----------------------------------------------------------------------

    private List<String> readRows(String target) throws IOException {
        for (Path candidate : candidates(target)) {
            if (Files.isRegularFile(candidate)) {
                System.out.println("[Report] Rutas activas cargadas desde: " + candidate.toAbsolutePath());
                return Files.readAllLines(candidate, StandardCharsets.UTF_8);
            }
        }

        InputStream is = ActiveLineLoader.class.getClassLoader()
                .getResourceAsStream(Paths.get(target).getFileName().toString());
        if (is == null) {
            is = ActiveLineLoader.class.getClassLoader().getResourceAsStream(target);
        }
        if (is != null) {
            System.out.println("[Report] Rutas activas cargadas desde classpath: " + target);
            List<String> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rows.add(line);
                }
            }
            return rows;
        }

        throw new IOException("Archivo no encontrado: " + target);
    }

    private List<Path> candidates(String target) {
        List<Path> paths = new ArrayList<>();
        Path configured = Paths.get(target);
        paths.add(configured);
        if (!configured.isAbsolute()) {
            String filename = configured.getFileName().toString();
            paths.add(Paths.get(filename));
            paths.add(Paths.get("..", filename));
            paths.add(Paths.get("..", "..", filename));
            paths.add(Paths.get("src", "main", "resources", filename));
            paths.add(Paths.get("/opt/sitm-mio", filename));
        }
        return paths;
    }

    private List<ActiveLine> parseRows(List<String> rows) {
        List<ActiveLine> lines = new ArrayList<>();
        boolean first = true;
        for (String row : rows) {
            if (first) { first = false; continue; }
            if (row == null || row.isBlank()) continue;

            List<String> cols = splitCsv(row);
            if (cols.size() < 4) continue;

            try {
                lines.add(new ActiveLine(
                        Integer.parseInt(cols.get(0).trim()),
                        cols.get(2).trim(),
                        cols.get(3).trim()));
            } catch (NumberFormatException ignored) {}
        }
        return lines;
    }

    private List<String> splitCsv(String row) {
        List<String>  cols    = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean       quoted  = false;

        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"'); i++;
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
