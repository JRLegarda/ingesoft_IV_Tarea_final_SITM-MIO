package org.example.core;

import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.Callable;

import org.example.model.Datagram;

/**
 * Processes a single byte-range chunk of the datagram file and returns a
 * {@link ChunkResult}.  Designed to be submitted to an {@code ExecutorService}.
 *
 * <h2>Overlap handling</h2>
 * <p>The chunk reads from {@code readStart} (which may be up to 1 MB before
 * {@code calculationStart}) so that the accumulator has prior bus positions
 * available at the chunk boundary.  However, only datagrams whose
 * {@code sourceOffset >= calculationStart} are counted in the speed tallies,
 * preventing double-counting with adjacent chunks.
 *
 * <h2>Partial-line handling</h2>
 * <p>If {@code readStart > 0} the first (possibly partial) line is discarded,
 * since the file pointer may land in the middle of a line.
 */
public class ChunkProcessor implements Callable<ChunkResult> {

    private final String filePath;
    private final long   readStart;
    private final long   readEnd;
    private final long   calculationStart;
    private final int    chunkIndex;
    
    // === LA PIEZA FALTA: Declarar el acumulador propio de este hilo ===
    private final RouteMonthAccumulator accumulator;

    public ChunkProcessor(
            String filePath,
            long   readStart,
            long   readEnd,
            long   calculationStart,
            int    chunkIndex) {

        this.filePath         = filePath;
        this.readStart        = readStart;
        this.readEnd          = readEnd;
        this.calculationStart = calculationStart;
        this.chunkIndex       = chunkIndex;
        
        // === LA PIEZA FALTA: Inicializar el acumulador con su offset lógico ===
        this.accumulator      = new RouteMonthAccumulator(calculationStart);
    }

    @Override
    public ChunkResult call() throws Exception {
        long startMs = System.currentTimeMillis();
        int linesRead = 0;
        int linesSkipped = 0;

        // 1. Calcular el tamaño exacto del chunk y crear un buffer en RAM
        long lengthToRead = readEnd - readStart;
        byte[] buffer = new byte[(int) lengthToRead];

        // 2. UNA SOLA lectura secuencial ultra rápida desde el disco a la RAM
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            raf.seek(readStart);
            raf.readFully(buffer);
        }

        int bufferPos = 0;

        // 3. Si no es el primer chunk, saltamos la primera línea (que puede estar incompleta)
        if (readStart > 0) {
            while (bufferPos < buffer.length && buffer[bufferPos] != '\n') {
                bufferPos++;
            }
            bufferPos++; // Avanzar después del '\n'
        }

        // 4. Procesar el buffer directamente en memoria
        int lineStart = bufferPos;
        while (bufferPos < buffer.length) {
            if (buffer[bufferPos] == '\n') {
                int lineEnd = bufferPos;
                
                // Manejar compatibilidad con saltos de línea de Windows (\r\n)
                if (lineEnd > lineStart && buffer[lineEnd - 1] == '\r') {
                    lineEnd--;
                }

                if (lineEnd > lineStart) {
                    // El offset real en el archivo se calcula matemáticamente en RAM.
                    long lineOffset = readStart + lineStart;

                    // Creamos el String únicamente si la línea está en el rango correcto
                    String line = new String(buffer, lineStart, lineEnd - lineStart, java.nio.charset.StandardCharsets.ISO_8859_1);
                    
                    if (!line.isBlank()) {
                        try {
                            Datagram d = new Datagram(line);
                            d.setSourceOffset(lineOffset);
                            accumulator.feed(d);
                            linesRead++;
                        } catch (IllegalArgumentException ignored) {
                            linesSkipped++;
                        }
                    }
                }
                lineStart = bufferPos + 1;
            }
            bufferPos++;
        }

        // Procesar la última línea si el archivo no termina en \n
        if (lineStart < buffer.length) {
            int lineEnd = buffer.length;
            if (lineEnd > lineStart && buffer[lineEnd - 1] == '\r') lineEnd--;
            if (lineEnd > lineStart) {
                long lineOffset = readStart + lineStart;
                String line = new String(buffer, lineStart, lineEnd - lineStart, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (!line.isBlank()) {
                    try {
                        Datagram d = new Datagram(line);
                        d.setSourceOffset(lineOffset);
                        accumulator.feed(d);
                        linesRead++;
                    } catch (IllegalArgumentException ignored) {
                        linesSkipped++;
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        List<RouteMonthResult> results = accumulator.getResults();

        System.out.printf(
                "[Chunk-%02d] completado | %,d ms | %,d datagramas | %,d velocidades | %,d ruta-mes%n",
                chunkIndex, elapsed,
                accumulator.getProcessedCount(),
                accumulator.getSpeedCount(),
                results.size());

        return new ChunkResult(
                "chunk-" + chunkIndex,
                elapsed,
                accumulator.getProcessedCount(),
                accumulator.getSpeedCount(),
                results);
    }
}