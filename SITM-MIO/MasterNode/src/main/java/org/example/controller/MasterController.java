package org.example.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;

import Demo.FileProviderPrx;
import Demo.TaskResult;
import Demo.WorkerPrx;
import org.example.util.ErrorLog;

public class MasterController implements IClusterControl {

    private static final int MAX_CHUNK_ATTEMPTS = 3;

    private final Communicator communicator;
    private final List<WorkerPrx> workers;

    public MasterController(Communicator communicator) {
        this.communicator = communicator;
        this.workers = new ArrayList<>();

        System.out.println("[Controller] Descubriendo workers en la red...");
        for (String proxy : loadWorkerProxies()) {
            addWorker(proxy);
        }
        System.out.println("[Controller] Workers activos y listos: " + workers.size());
        System.out.println();

        if (workers.isEmpty()) {
            System.err.println("[Controller] ADVERTENCIA: No hay workers disponibles.");
            System.err.println("[Controller] Asegurate de levantar los WorkerNode antes de iniciar el Master.");
        }
    }

    private void addWorker(String proxyString) {
        try {
            System.out.print("[Controller] Conectando a " + proxyString + " ... ");
            ObjectPrx base = communicator.stringToProxy(proxyString);
            WorkerPrx worker = WorkerPrx.checkedCast(base);

            if (worker != null) {
                worker.ice_ping();
                workers.add(worker);
                System.out.println("OK");
            } else {
                System.out.println("ERROR: Proxy invalido");
            }
        } catch (Exception e) {
            System.out.println("ERROR: No se pudo conectar -> " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public List<TaskResult> executeWorkStealing(
            FileProviderPrx fileProvider,
            ConcurrentLinkedQueue<long[]> chunkQueue,
            int remoteReadSizeBytes,
            boolean verbose
    ) {
        List<TaskResult> finalResults = new ArrayList<>();
        if (workers.isEmpty()) {
            return finalResults;
        }

        ConcurrentLinkedQueue<ChunkTask> taskQueue = new ConcurrentLinkedQueue<>();
        for (long[] chunk : chunkQueue) {
            taskQueue.add(new ChunkTask(chunk[0], chunk[1], chunk[2]));
        }

        List<WorkerThread> threads = new ArrayList<>();
        List<List<TaskResult>> partialResultsPerWorker = new ArrayList<>();
        AtomicInteger completedChunks = new AtomicInteger(0);
        AtomicInteger terminalChunks = new AtomicInteger(0);
        AtomicInteger failedChunks = new AtomicInteger(0);
        int totalChunks = taskQueue.size();

        for (WorkerPrx worker : workers) {
            List<TaskResult> workerTaskList = java.util.Collections.synchronizedList(new ArrayList<>());
            partialResultsPerWorker.add(workerTaskList);

            WorkerThread thread = new WorkerThread(
                    worker,
                    fileProvider,
                    taskQueue,
                    workerTaskList,
                    completedChunks,
                    terminalChunks,
                    failedChunks,
                    totalChunks,
                    remoteReadSizeBytes,
                    verbose
            );
            threads.add(thread);
        }

        for (WorkerThread thread : threads) {
            thread.start();
        }

        for (WorkerThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println("[Controller] Hilo principal interrumpido esperando workers");
                Thread.currentThread().interrupt();
            }
        }

        for (List<TaskResult> subList : partialResultsPerWorker) {
            finalResults.addAll(subList);
        }

        if (failedChunks.get() > 0) {
            throw new IllegalStateException("Fallaron " + failedChunks.get() + " chunks despues de "
                    + MAX_CHUNK_ATTEMPTS + " intentos. Se aborta para evitar un CSV incompleto.");
        }

        return finalResults;
    }

    private String[] loadWorkerProxies() {
        String configuredHosts = communicator.getProperties().getPropertyWithDefault("Master.WorkerHosts", "localhost:10000");
        String[] hosts = splitConfiguredValues(configuredHosts);
        String[] proxies = new String[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            proxies[i] = toWorkerProxy(hosts[i]);
        }
        return proxies;
    }

    private String[] splitConfiguredValues(String value) {
        return java.util.Arrays.stream(value.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private String toWorkerProxy(String hostConfig) {
        if (hostConfig.contains(":tcp")) {
            return hostConfig;
        }
        String host = hostConfig;
        String port = "10000";
        int separator = hostConfig.lastIndexOf(':');
        if (separator > -1) {
            host = hostConfig.substring(0, separator);
            port = hostConfig.substring(separator + 1);
        }
        String timeout = communicator.getProperties().getPropertyWithDefault("Master.WorkerTimeoutMs", "600000");
        return "SimpleWorker:tcp -h " + host + " -p " + port + " -t " + timeout;
    }

    private static class WorkerThread extends Thread {
        private final WorkerPrx worker;
        private final FileProviderPrx fileProvider;
        private final ConcurrentLinkedQueue<ChunkTask> chunkQueue;
        private final List<TaskResult> resultsCollector;
        private final AtomicInteger completedChunks;
        private final AtomicInteger terminalChunks;
        private final AtomicInteger failedChunks;
        private final int totalChunks;
        private final int remoteReadSizeBytes;
        private final boolean verbose;

        private WorkerThread(
                WorkerPrx worker,
                FileProviderPrx fileProvider,
                ConcurrentLinkedQueue<ChunkTask> chunkQueue,
                List<TaskResult> resultsCollector,
                AtomicInteger completedChunks,
                AtomicInteger terminalChunks,
                AtomicInteger failedChunks,
                int totalChunks,
                int remoteReadSizeBytes,
                boolean verbose
        ) {
            this.worker = worker;
            this.fileProvider = fileProvider;
            this.chunkQueue = chunkQueue;
            this.resultsCollector = resultsCollector;
            this.completedChunks = completedChunks;
            this.terminalChunks = terminalChunks;
            this.failedChunks = failedChunks;
            this.totalChunks = totalChunks;
            this.remoteReadSizeBytes = remoteReadSizeBytes;
            this.verbose = verbose;
        }

        @Override
        public void run() {
            while (terminalChunks.get() < totalChunks) {
                ChunkTask chunk = chunkQueue.poll();
                if (chunk == null) {
                    sleepBriefly();
                    continue;
                }

                try {
                    long attemptStartMs = System.currentTimeMillis();
                    TaskResult result = worker.processDatagramLog(
                            fileProvider,
                            chunk.startOffset,
                            chunk.endOffset,
                            chunk.calculationStartOffset,
                            remoteReadSizeBytes,
                            verbose
                    );
                    if (result == null) {
                        throw new IllegalStateException("Worker retorno resultado nulo");
                    }

                    long attemptMs = System.currentTimeMillis() - attemptStartMs;
                    resultsCollector.add(result);
                    int done = completedChunks.incrementAndGet();
                    terminalChunks.incrementAndGet();
                    if (done == totalChunks || done % Math.max(1, totalChunks / 20) == 0) {
                        System.out.println("[Controller] Progreso chunks: " + done + "/" + totalChunks
                                + " | ultimoChunk=[" + chunk.startOffset + "-" + chunk.endOffset + "]"
                                + " | worker=" + result.workerName
                                + " | remoteMs=" + attemptMs
                                + " | workerMs=" + result.executionTime);
                    }
                } catch (Exception e) {
                    chunk.attempts++;
                    if (chunk.attempts < MAX_CHUNK_ATTEMPTS) {
                        logChunkFailure("Reintentando", chunk, e);
                        chunkQueue.add(chunk);
                    } else {
                        failedChunks.incrementAndGet();
                        terminalChunks.incrementAndGet();
                        logChunkFailure("Chunk fallido definitivamente", chunk, e);
                    }
                }
            }
        }

        private void sleepBriefly() {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void logChunkFailure(String action, ChunkTask chunk, Exception e) {
            System.err.println("[Controller][ERROR] " + action
                    + " | chunk=[" + chunk.startOffset + "-" + chunk.endOffset + "]"
                    + " | calcStart=" + chunk.calculationStartOffset
                    + " | attempt=" + chunk.attempts + "/" + MAX_CHUNK_ATTEMPTS
                    + " | remoteReadSizeBytes=" + remoteReadSizeBytes
                    + " | workerProxy=" + worker
                    + " | error=" + ErrorLog.describe(e)
                    + " | at=" + ErrorLog.topStack(e));
        }
    }

    private static class ChunkTask {
        private final long startOffset;
        private final long endOffset;
        private final long calculationStartOffset;
        private int attempts;

        private ChunkTask(long startOffset, long endOffset, long calculationStartOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.calculationStartOffset = calculationStartOffset;
        }
    }
}
