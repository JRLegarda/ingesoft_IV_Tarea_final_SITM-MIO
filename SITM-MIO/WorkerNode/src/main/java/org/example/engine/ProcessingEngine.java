package org.example.engine;

import Demo.FileProviderPrx;
import Demo.TaskResult;
import Demo.Worker;
import com.zeroc.Ice.Current;
import org.example.core.ChunkProcessor;
import org.example.core.ChunkResult;
import org.example.core.RouteMonthResult;
import org.example.util.ErrorLog;

import java.util.List;

public class ProcessingEngine implements Worker {

    private final ChunkProcessor chunkProcessor = new ChunkProcessor();

    @Override
    public TaskResult processDatagramLog(
            FileProviderPrx fileProvider,
            long startOffset,
            long endOffset,
            long calculationStartOffset,
            int requestedRemoteReadSizeBytes,
            boolean requestedVerbose,
            Current current
    ) {
        int remoteReadSizeBytes = requestedRemoteReadSizeBytes > 0
                ? requestedRemoteReadSizeBytes
                : DatagramReader.DEFAULT_REMOTE_READ_SIZE;
        boolean verbose = requestedVerbose;

        if (verbose) {
            System.out.println();
            System.out.println("[Engine] Nueva tarea recibida del Master");
            System.out.println("[Engine] Fuente: lectura remota por demanda via Ice");
            System.out.println("[Engine] Lectura: [" + String.format("%,d", startOffset) + " - " +
                    String.format("%,d", endOffset) + "] bytes");
            System.out.println("[Engine] Calculo desde offset: " + String.format("%,d", calculationStartOffset));
        }

        ChunkResult result;
        try {
            result = chunkProcessor.process(
                    fileProvider,
                    startOffset,
                    endOffset,
                    calculationStartOffset,
                    remoteReadSizeBytes,
                    verbose
            );
        } catch (RuntimeException e) {
            System.err.println("[Engine][ERROR] ChunkProcessor fallo"
                    + " | range=[" + startOffset + "-" + endOffset + "]"
                    + " | calcStart=" + calculationStartOffset
                    + " | remoteReadSizeBytes=" + remoteReadSizeBytes
                    + " | error=" + ErrorLog.describe(e)
                    + " | at=" + ErrorLog.topStack(e));
            throw e;
        }

        if (verbose) {
            printSummary(result);
        }

        return new TaskResult(
                result.getWorkerName(),
                result.getExecutionTime(),
                result.getProcessedCount(),
                result.getSpeedCount(),
                toSliceRouteMonthSpeeds(result.getRouteMonthResults())
        );
    }

    private void printSummary(ChunkResult result) {
        System.out.println();
        System.out.println("[Engine] Tarea completada");
        System.out.println("[Engine] Datagramas procesados:  " + String.format("%,d", result.getProcessedCount()));
        System.out.println("[Engine] Velocidades calculadas: " + String.format("%,d", result.getSpeedCount()));
        System.out.println("[Engine] Rutas-mes generadas:     " + String.format("%,d", result.getRouteMonthResults().size()));
        System.out.println("[Engine] Tiempo total:           " + String.format("%,d ms", result.getExecutionTime()));
    }

    private Demo.RouteMonthSpeed[] toSliceRouteMonthSpeeds(List<RouteMonthResult> entries) {
        return entries.stream()
                .map(entry -> new Demo.RouteMonthSpeed(
                        entry.getLineId(),
                        entry.getMonth(),
                        entry.getSpeedSum(),
                        entry.getCount()
                ))
                .toArray(Demo.RouteMonthSpeed[]::new);
    }

}
