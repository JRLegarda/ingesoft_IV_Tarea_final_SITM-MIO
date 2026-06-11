package org.example.concurrent;

import org.example.core.ChunkProcessor;
import org.example.core.ChunkResult;
import org.example.core.RouteMonthResult;
import org.example.report.ActiveLine;
import org.example.report.ActiveLineLoader;
import org.example.report.RouteMonthCsvWriter;
import org.example.scheduler.LocalChunkPlanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * SITM-MIO — Versión Concurrente ThreadPool.
 *
 * <p>Divides the datagram file into 10 MB chunks (with 1 MB overlap) and
 * processes each chunk in parallel using a fixed thread pool.  All chunk
 * results are merged by summing {@code speedSum} and {@code count} before
 * computing the final averages — never averaging averages.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar build/libs/sitm-mio-threadpool.jar [datagramPath [threadCount [activeLinesPath]]]
 * </pre>
 *
 * <p>Defaults:
 * <ul>
 *   <li>datagramPath   — {@code data/datagrams-MiniPilot.csv}</li>
 *   <li>threadCount    — {@code Runtime.getRuntime().availableProcessors()}</li>
 *   <li>activeLinesPath— {@code lines-241-ActiveGT.csv} (also searched on classpath)</li>
 * </ul>
 */
public class ThreadPoolApp {

    private static final String DEFAULT_DATAGRAM_PATH = "data/datagrams-MiniPilot.csv";
    private static final String DEFAULT_ACTIVE_LINES  = "lines-241-ActiveGT.csv";
    private static final String DEFAULT_OUTPUT_CSV    = "route_month_speeds_threadpool.csv";

    public static void main(String[] args) throws IOException {

        String datagramPath    = args.length > 0 ? args[0] : DEFAULT_DATAGRAM_PATH;
        int    threadCount     = args.length > 1 ? parseThreadCount(args[1])
                                                 : Runtime.getRuntime().availableProcessors();
        String activeLinesPath = args.length > 2 ? args[2] : DEFAULT_ACTIVE_LINES;
        String outputPath      = DEFAULT_OUTPUT_CSV;

        // ---------------------------------------------------------------
        // Validate input file
        // ---------------------------------------------------------------
        File inputFile = new File(datagramPath);
        if (!inputFile.exists()) {
            System.err.println("[ThreadPool] ERROR: Archivo de datagramas no encontrado: "
                    + inputFile.getAbsolutePath());
            System.err.println("[ThreadPool] Coloca el archivo en: data/datagrams-MiniPilot.csv");
            System.err.println("[ThreadPool] O pasa la ruta como primer argumento.");
            System.exit(1);
        }

        long fileSize = inputFile.length();

        // ---------------------------------------------------------------
        // Plan chunks
        // ---------------------------------------------------------------
        LocalChunkPlanner   planner = new LocalChunkPlanner();
        List<ChunkProcessor> tasks  = planner.plan(datagramPath, fileSize);
        int numChunks = tasks.size();

        printHeader(datagramPath, fileSize, threadCount, numChunks,
                planner.getChunkSize(), planner.getOverlapBytes(), activeLinesPath, outputPath);

        // ---------------------------------------------------------------
        // Submit chunks to thread pool
        // ---------------------------------------------------------------
        long globalStart = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ChunkResult>> futures = new ArrayList<>(numChunks);
        for (ChunkProcessor task : tasks) {
            futures.add(executor.submit(task));
        }
        executor.shutdown();

        // ---------------------------------------------------------------
        // Collect results
        // ---------------------------------------------------------------
        List<ChunkResult> chunkResults = new ArrayList<>(numChunks);
        boolean hasErrors = false;

        for (int i = 0; i < futures.size(); i++) {
            try {
                chunkResults.add(futures.get(i).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.printf("[ThreadPool] Chunk %d interrumpido: %s%n", i + 1, e.getMessage());
                hasErrors = true;
            } catch (ExecutionException e) {
                System.err.printf("[ThreadPool] ERROR en chunk %d: %s%n", i + 1,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                hasErrors = true;
            }
        }

        long totalMs = System.currentTimeMillis() - globalStart;

        // ---------------------------------------------------------------
        // Guard: do not produce misleading CSV if any chunk failed
        // ---------------------------------------------------------------
        if (hasErrors || chunkResults.size() != numChunks) {
            System.err.println();
            System.err.println("[ThreadPool] ERROR: La ejecucion tuvo fallos. CSV NO generado.");
            System.err.printf ("[ThreadPool] Chunks esperados: %d | Chunks completados: %d%n",
                    numChunks, chunkResults.size());
            System.exit(2);
        }

        // ---------------------------------------------------------------
        // Merge results (sum speedSum and count — never average averages)
        // ---------------------------------------------------------------
        List<RouteMonthResult> merged = mergeResults(chunkResults);

        // ---------------------------------------------------------------
        // Write CSV
        // ---------------------------------------------------------------
        List<ActiveLine> activeLines = new ActiveLineLoader().load(activeLinesPath);
        if (activeLines.isEmpty()) {
            System.err.println("[ThreadPool] ADVERTENCIA: No se cargaron rutas activas.");
        }

        Path outputAbsolute = new RouteMonthCsvWriter().writeMatrix(merged, activeLines, outputPath);

        // ---------------------------------------------------------------
        // Print summary
        // ---------------------------------------------------------------
        printSummary(datagramPath, fileSize, threadCount, chunkResults, merged, totalMs,
                outputAbsolute.toString());
    }

    // -----------------------------------------------------------------------
    // Merge helpers
    // -----------------------------------------------------------------------

    /**
     * Merges all route-month results from all chunks.
     * Uses raw sum/count aggregation — no averaging of averages.
     */
    private static List<RouteMonthResult> mergeResults(List<ChunkResult> chunkResults) {
        java.util.Map<String, double[]> map = new java.util.LinkedHashMap<>();

        for (ChunkResult cr : chunkResults) {
            for (RouteMonthResult r : cr.getRouteMonthResults()) {
                String key = r.getLineId() + "|" + r.getMonth();
                double[] totals = map.computeIfAbsent(key, k -> new double[]{0.0, 0.0});
                totals[0] += r.getSpeedSum();
                totals[1] += r.getCount();
            }
        }

        List<RouteMonthResult> merged = new ArrayList<>(map.size());
        for (java.util.Map.Entry<String, double[]> e : map.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            int    lineId = Integer.parseInt(parts[0]);
            String month  = parts[1];
            merged.add(new RouteMonthResult(lineId, month, e.getValue()[0], (int) e.getValue()[1]));
        }
        return merged;
    }

    // -----------------------------------------------------------------------
    // Console helpers
    // -----------------------------------------------------------------------

    private static void printHeader(String datagramPath, long fileSize,
                                    int threadCount, int numChunks,
                                    long chunkSize, long overlapBytes,
                                    String activeLinesPath, String outputPath) {
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SITM-MIO — Versión Concurrente ThreadPool");
        System.out.println("=======================================================");
        System.out.printf("[ThreadPool] Archivo de entrada:    %s%n", datagramPath);
        System.out.printf("[ThreadPool] Tamano del archivo:    %,d bytes (%.2f MB)%n",
                fileSize, fileSize / 1_048_576.0);
        System.out.printf("[ThreadPool] Tamano de chunk:       %.2f MB%n", chunkSize / 1_048_576.0);
        System.out.printf("[ThreadPool] Solapamiento:          %.2f MB%n", overlapBytes / 1_048_576.0);
        System.out.printf("[ThreadPool] Total de chunks:       %d%n", numChunks);
        System.out.printf("[ThreadPool] Hilos del pool:        %d%n", threadCount);
        System.out.printf("[ThreadPool] Rutas activas:         %s%n", activeLinesPath);
        System.out.printf("[ThreadPool] Archivo de salida:     %s%n", outputPath);
        System.out.println("-------------------------------------------------------");
        System.out.println("[ThreadPool] Iniciando procesamiento paralelo...");
        System.out.println();
    }

    private static void printSummary(String datagramPath, long fileSize,
                                     int threadCount,
                                     List<ChunkResult> chunkResults,
                                     List<RouteMonthResult> merged,
                                     long totalMs,
                                     String outputAbsolute) {

        int    totalProcessed  = chunkResults.stream().mapToInt(ChunkResult::getProcessedCount).sum();
        int    totalSpeed      = chunkResults.stream().mapToInt(ChunkResult::getSpeedCount).sum();
        long   minChunk        = chunkResults.stream().mapToLong(ChunkResult::getExecutionTimeMs).min().orElse(0);
        long   maxChunk        = chunkResults.stream().mapToLong(ChunkResult::getExecutionTimeMs).max().orElse(0);
        long   sumChunkMs      = chunkResults.stream().mapToLong(ChunkResult::getExecutionTimeMs).sum();

        // Unique route-month cells after merge
        Set<String> cells = new HashSet<>();
        for (RouteMonthResult r : merged) cells.add(r.getLineId() + "|" + r.getMonth());

        double totalSeconds  = totalMs / 1_000.0;
        double throughputMBs = totalSeconds > 0 ? (fileSize / 1_048_576.0) / totalSeconds : 0.0;
        double speedup       = totalMs > 0 ? (double) sumChunkMs / totalMs : 0.0;

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SITM-MIO — Reporte Final");
        System.out.println("=======================================================");
        System.out.printf("[ThreadPool] Archivo de entrada:         %s%n", datagramPath);
        System.out.printf("[ThreadPool] Tamano del archivo:         %,d bytes (%.2f MB)%n",
                fileSize, fileSize / 1_048_576.0);
        System.out.printf("[ThreadPool] Hilos del pool:             %d%n", threadCount);
        System.out.println("-------------------------------------------------------");
        System.out.printf("[ThreadPool] Datagramas procesados:      %,d%n",  totalProcessed);
        System.out.printf("[ThreadPool] Mediciones de velocidad:    %,d%n",  totalSpeed);
        System.out.printf("[ThreadPool] Celdas ruta-mes con datos:  %,d%n",  cells.size());
        System.out.println("-------------------------------------------------------");
        System.out.printf("[ThreadPool] Tiempo total (wall-clock):  %,d ms (%.2f s)%n",
                totalMs, totalSeconds);
        System.out.printf("[ThreadPool] Tiempo acumulado chunks:    %,d ms%n", sumChunkMs);
        System.out.printf("[ThreadPool] Speedup operativo:          %.2fx%n", speedup);
        System.out.printf("[ThreadPool] Throughput:                 %.2f MB/s%n", throughputMBs);
        System.out.printf("[ThreadPool] Chunk mas rapido:           %,d ms%n", minChunk);
        System.out.printf("[ThreadPool] Chunk mas lento:            %,d ms%n", maxChunk);
        System.out.println("-------------------------------------------------------");
        System.out.println("[ThreadPool] Detalle por chunk:");
        for (ChunkResult cr : chunkResults) {
            System.out.printf("  %-12s | %,6d ms | %,8d datagramas | %,8d velocidades | %,5d ruta-mes%n",
                    cr.getLabel(),
                    cr.getExecutionTimeMs(),
                    cr.getProcessedCount(),
                    cr.getSpeedCount(),
                    cr.getRouteMonthResults().size());
        }
        System.out.println("-------------------------------------------------------");
        System.out.printf("[ThreadPool] CSV generado:               %s%n", outputAbsolute);
        System.out.println("=======================================================");
        System.out.println();
    }

    private static int parseThreadCount(String arg) {
        try {
            int n = Integer.parseInt(arg);
            if (n < 1) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            System.err.println("[ThreadPool] Numero de hilos invalido '" + arg
                    + "', usando availableProcessors.");
            return Runtime.getRuntime().availableProcessors();
        }
    }
}
