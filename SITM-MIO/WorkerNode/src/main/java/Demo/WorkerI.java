package Demo;

import com.zeroc.Ice.Current;
import org.example.engine.ProcessingEngine;
import org.example.util.ErrorLog;

public class WorkerI implements Demo.Worker {

    private final ProcessingEngine engine = new ProcessingEngine();

    @Override
    public TaskResult processDatagramLog(
            FileProviderPrx fileProvider,
            long startOffset,
            long endOffset,
            long calculationStartOffset,
            int remoteReadSizeBytes,
            boolean verbose,
            Current current
    ) {
        String workerUser = System.getProperty("user.name", "unknown");
        long startMs = System.currentTimeMillis();
        if (verbose) {
            System.out.println("Recibida tarea desde el servicio remoto de archivos del Master");
            System.out.println("Rango de bytes: " + startOffset + " -> " + endOffset);
        }

        try {
            TaskResult result = engine.processDatagramLog(
                    fileProvider,
                    startOffset,
                    endOffset,
                    calculationStartOffset,
                    remoteReadSizeBytes,
                    verbose,
                    current
            );
            if (verbose) {
                System.out.println("[Worker] Chunk completado"
                        + " | worker=" + workerUser
                        + " | range=[" + startOffset + "-" + endOffset + "]"
                        + " | elapsedMs=" + (System.currentTimeMillis() - startMs));
            }
            return result;
        } catch (RuntimeException e) {
            System.err.println("[Worker][ERROR] processDatagramLog fallo"
                    + " | worker=" + workerUser
                    + " | range=[" + startOffset + "-" + endOffset + "]"
                    + " | calcStart=" + calculationStartOffset
                    + " | remoteReadSizeBytes=" + remoteReadSizeBytes
                    + " | elapsedMs=" + (System.currentTimeMillis() - startMs)
                    + " | error=" + ErrorLog.describe(e)
                    + " | at=" + ErrorLog.topStack(e));
            throw e;
        }
    }
}
