package org.example.scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.example.controller.IClusterControl;
import org.example.controller.MasterController;
import org.example.report.ActiveLine;
import org.example.report.ActiveLineLoader;
import org.example.report.RouteMonthCsvWriter;

import Demo.FileProviderPrx;
import Demo.TaskResult;

public class JobScheduler implements IJobManager {

    public static final long DEFAULT_CHUNK_SIZE_BYTES = 1024L * 1024L * 128L;
    public static final long DEFAULT_CHUNK_OVERLAP_BYTES = 1024L * 1024L;
    public static final int DEFAULT_REMOTE_READ_SIZE_BYTES = 1024 * 1024 * 8;

    private final IClusterControl controller;
    private final String activeLinesPath;
    private final String outputCsvPath;
    private final long chunkSizeBytes;
    private final long chunkOverlapBytes;
    private final int remoteReadSizeBytes;
    private final boolean verbose;
    private FileProviderPrx fileProvider;

    public JobScheduler(IClusterControl controller) {
        this(
                controller,
                System.getProperty("sitm.linesFile", "lines-241-ActiveGT.csv"),
                System.getProperty("sitm.outputCsv", "route_month_speeds.csv"),
                Long.parseLong(System.getProperty("sitm.chunkSizeBytes", Long.toString(DEFAULT_CHUNK_SIZE_BYTES))),
                Long.parseLong(System.getProperty("sitm.chunkOverlapBytes", Long.toString(DEFAULT_CHUNK_OVERLAP_BYTES))),
                Integer.parseInt(System.getProperty("sitm.remoteReadSizeBytes", Integer.toString(DEFAULT_REMOTE_READ_SIZE_BYTES))),
                Boolean.parseBoolean(System.getProperty("sitm.verbose", "false"))
        );
    }

    public JobScheduler(
            IClusterControl controller,
            String activeLinesPath,
            String outputCsvPath,
            long chunkSizeBytes,
            long chunkOverlapBytes,
            int remoteReadSizeBytes,
            boolean verbose
    ) {
        this.controller = controller;
        this.activeLinesPath = activeLinesPath;
        this.outputCsvPath = outputCsvPath;
        this.chunkSizeBytes = chunkSizeBytes;
        this.chunkOverlapBytes = chunkOverlapBytes;
        this.remoteReadSizeBytes = remoteReadSizeBytes;
        this.verbose = verbose;
    }

    public void setFileProvider(FileProviderPrx fileProvider) {
        this.fileProvider = fileProvider;
    }

    @Override
    public void scheduleJob(String filePath) {
        long startJobTime = System.currentTimeMillis();
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("[Scheduler] ERROR: Archivo de datagramas no encontrado: " + filePath);
            return;
        }
        if (fileProvider == null) {
            System.err.println("[Scheduler] ERROR: No hay servicio remoto de lectura inicializado en el Master.");
            return;
        }

        long fileSize = fileProvider.fileSize();
        System.out.println("[Scheduler] Analisis distribuido iniciado");
        System.out.println("[Scheduler] Archivo: " + filePath + " (" + fileSize + " bytes)");
        System.out.println("[Scheduler] ChunkSizeBytes: " + chunkSizeBytes);
        System.out.println("[Scheduler] ChunkOverlapBytes: " + chunkOverlapBytes);
        System.out.println("[Scheduler] RemoteReadSizeBytes: " + remoteReadSizeBytes);
        System.out.println("[Scheduler] Verbose: " + verbose);

        List<long[]> chunks = new ArrayList<>();
        long currentOffset = 0L;
        while (currentOffset < fileSize) {
            long calculationStart = currentOffset;
            long readStart = Math.max(0L, calculationStart - chunkOverlapBytes);
            long readEnd = Math.min(calculationStart + chunkSizeBytes, fileSize);

            chunks.add(new long[]{readStart, readEnd, calculationStart});
            currentOffset = readEnd;
        }

        System.out.println("[Scheduler] Total chunks planificados: " + chunks.size());
        ConcurrentLinkedQueue<long[]> chunkQueue = new ConcurrentLinkedQueue<>(chunks);

        System.out.println("[Scheduler] Despachando tareas mediante balanceo dinamico (work-stealing)...");
        List<TaskResult> results;
        try {
            results = ((MasterController) controller).executeWorkStealing(
                    fileProvider,
                    chunkQueue,
                    remoteReadSizeBytes,
                    verbose
            );
        } catch (RuntimeException e) {
            System.err.println("[Scheduler] ERROR: Analisis abortado. " + e.getMessage());
            return;
        }

        long processingTime = System.currentTimeMillis() - startJobTime;

        System.out.println("[Scheduler] Chunks completados. Generando matriz final...");
        ActiveLineLoader loader = new ActiveLineLoader();
        List<ActiveLine> activeLines = loader.load(activeLinesPath);

        RouteMonthCsvWriter writer = new RouteMonthCsvWriter();
        try {
            Path outPath = writer.writeMatrix(results, activeLines, outputCsvPath);
            printSummary(results, processingTime, outPath.toAbsolutePath().toString());
        } catch (IOException e) {
            System.err.println("[Scheduler] ERROR escribiendo CSV de salida: " + e.getMessage());
        }
    }

    private void printSummary(List<TaskResult> results, long totalTimeMs, String outputAbsolute) {
        int totalDatagrams = 0;
        int totalSpeeds = 0;
        for (TaskResult res : results) {
            totalDatagrams += res.processedCount;
            totalSpeeds += res.speedCount;
        }

        int routeMonthCells = countRouteMonthCells(results);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("SITM-MIO - REPORTE DISTRIBUIDO FINAL");
        System.out.println("=======================================================");
        System.out.printf("[Scheduler] Tiempo total cluster:      %,d ms%n", totalTimeMs);
        System.out.printf("[Scheduler] Total datagramas:          %,d%n", totalDatagrams);
        System.out.printf("[Scheduler] Total velocidades:         %,d%n", totalSpeeds);
        System.out.printf("[Scheduler] Celdas ruta-mes:           %,d%n", routeMonthCells);
        System.out.printf("[Scheduler] CSV final guardado en:     %s%n", outputAbsolute);
        System.out.println("=======================================================");
    }

    private int countRouteMonthCells(List<TaskResult> results) {
        Set<String> keys = new HashSet<>();
        for (TaskResult result : results) {
            if (result.routeMonthSpeeds == null) continue;
            for (Demo.RouteMonthSpeed speed : result.routeMonthSpeeds) {
                keys.add(speed.lineId + "|" + speed.month);
            }
        }
        return keys.size();
    }
}
