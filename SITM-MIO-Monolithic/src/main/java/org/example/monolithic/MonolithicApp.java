package org.example.monolithic;

import org.example.core.ChunkResult;
import org.example.core.RouteMonthAccumulator;
import org.example.core.RouteMonthResult;
import org.example.io.DatagramFileReader;
import org.example.report.ActiveLine;
import org.example.report.ActiveLineLoader;
import org.example.report.RouteMonthCsvWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * SITM-MIO — Versión Monolítica.
 *
 * <p>Processes a datagram CSV file sequentially (single thread) and outputs a
 * route × month average-speed matrix to {@code route_month_speeds_monolithic.csv}.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar build/libs/sitm-mio-monolithic.jar [datagramPath [activeLinesPath]]
 * </pre>
 *
 * <p>Defaults:
 * <ul>
 *   <li>datagramPath  — {@code data/datagrams-MiniPilot.csv}</li>
 *   <li>activeLinesPath — {@code lines-241-ActiveGT.csv} (also searched on classpath)</li>
 * </ul>
 */
public class MonolithicApp {

    private static final String DEFAULT_DATAGRAM_PATH   = "data/datagrams-MiniPilot.csv";
    private static final String DEFAULT_ACTIVE_LINES    = "lines-241-ActiveGT.csv";
    private static final String DEFAULT_OUTPUT_CSV      = "route_month_speeds_monolithic.csv";

    public static void main(String[] args) throws IOException {

        String datagramPath   = args.length > 0 ? args[0] : DEFAULT_DATAGRAM_PATH;
        String activeLinesPath= args.length > 1 ? args[1] : DEFAULT_ACTIVE_LINES;
        String outputPath     = DEFAULT_OUTPUT_CSV;

        // ---------------------------------------------------------------
        // Validate input file
        // ---------------------------------------------------------------
        File inputFile = new File(datagramPath);
        if (!inputFile.exists()) {
            System.err.println("[Monolithic] ERROR: Archivo de datagramas no encontrado: "
                    + inputFile.getAbsolutePath());
            System.err.println("[Monolithic] Coloca el archivo en:  data/datagrams-MiniPilot.csv");
            System.err.println("[Monolithic] O pasa la ruta como primer argumento.");
            System.exit(1);
        }

        long fileSize = inputFile.length();

        printHeader(datagramPath, fileSize, activeLinesPath, outputPath);

        // ---------------------------------------------------------------
        // Process file
        // ---------------------------------------------------------------
        long globalStart = System.currentTimeMillis();

        RouteMonthAccumulator accumulator = new RouteMonthAccumulator();
        DatagramFileReader    reader      = new DatagramFileReader(datagramPath);

        DatagramFileReader.ReadStats readStats = reader.read(accumulator::feed);

        long totalMs = System.currentTimeMillis() - globalStart;

        // ---------------------------------------------------------------
        // Collect results
        // ---------------------------------------------------------------
        List<RouteMonthResult> results = accumulator.getResults();

        ChunkResult chunk = new ChunkResult(
                "monolithic",
                totalMs,
                accumulator.getProcessedCount(),
                accumulator.getSpeedCount(),
                results);

        // ---------------------------------------------------------------
        // Write CSV
        // ---------------------------------------------------------------
        List<ActiveLine> activeLines = new ActiveLineLoader().load(activeLinesPath);
        if (activeLines.isEmpty()) {
            System.err.println("[Monolithic] ADVERTENCIA: No se cargaron rutas activas. "
                    + "El CSV solo contendra encabezados.");
        }

        RouteMonthCsvWriter writer = new RouteMonthCsvWriter();
        java.nio.file.Path outputAbsolute = writer.writeMatrix(results, activeLines, outputPath);

        // ---------------------------------------------------------------
        // Print summary
        // ---------------------------------------------------------------
        printSummary(datagramPath, fileSize, chunk, readStats, outputAbsolute.toString());
    }

    // -----------------------------------------------------------------------
    // Console helpers
    // -----------------------------------------------------------------------

    private static void printHeader(String datagramPath, long fileSize,
                                    String activeLinesPath, String outputPath) {
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SITM-MIO — Versión Monolítica");
        System.out.println("=======================================================");
        System.out.printf("[Monolithic] Archivo de entrada:    %s%n", datagramPath);
        System.out.printf("[Monolithic] Tamano del archivo:    %,d bytes (%.2f MB)%n",
                fileSize, fileSize / 1_048_576.0);
        System.out.printf("[Monolithic] Rutas activas:         %s%n", activeLinesPath);
        System.out.printf("[Monolithic] Archivo de salida:     %s%n", outputPath);
        System.out.println("-------------------------------------------------------");
        System.out.println("[Monolithic] Iniciando procesamiento secuencial...");
        System.out.println();
    }

    private static void printSummary(String datagramPath, long fileSize,
                                     ChunkResult chunk,
                                     DatagramFileReader.ReadStats readStats,
                                     String outputAbsolute) {
        double totalSeconds  = chunk.getExecutionTimeMs() / 1_000.0;
        double throughputMBs = totalSeconds > 0
                ? (fileSize / 1_048_576.0) / totalSeconds
                : 0.0;

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SITM-MIO — Reporte Final");
        System.out.println("=======================================================");
        System.out.printf("[Monolithic] Archivo de entrada:        %s%n", datagramPath);
        System.out.printf("[Monolithic] Tamano del archivo:        %,d bytes (%.2f MB)%n",
                fileSize, fileSize / 1_048_576.0);
        System.out.println("-------------------------------------------------------");
        System.out.printf("[Monolithic] Lineas validas leidas:     %,d%n",  readStats.validLines);
        System.out.printf("[Monolithic] Lineas omitidas:           %,d%n",  readStats.skippedLines);
        System.out.printf("[Monolithic] Datagramas procesados:     %,d%n",  chunk.getProcessedCount());
        System.out.printf("[Monolithic] Mediciones de velocidad:   %,d%n",  chunk.getSpeedCount());
        System.out.printf("[Monolithic] Celdas ruta-mes con datos: %,d%n",  chunk.getRouteMonthResults().size());
        System.out.println("-------------------------------------------------------");
        System.out.printf("[Monolithic] Tiempo total:              %,d ms (%.2f s)%n",
                chunk.getExecutionTimeMs(), totalSeconds);
        System.out.printf("[Monolithic] Throughput:                %.2f MB/s%n", throughputMBs);
        System.out.println("-------------------------------------------------------");
        System.out.printf("[Monolithic] CSV generado:              %s%n", outputAbsolute);
        System.out.println("=======================================================");
        System.out.println();
    }
}
