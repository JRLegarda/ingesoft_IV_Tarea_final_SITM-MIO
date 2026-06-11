package org.example.engine;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.BlockingQueue;

import org.example.model.Datagram;
import org.example.util.ErrorLog;

import Demo.FileProviderPrx;

public class DatagramReader implements Runnable {

    private static final int LOCAL_BUFFER_SIZE = 1024 * 1024;
    public static final int DEFAULT_REMOTE_READ_SIZE = 8 * 1024 * 1024;

    private final String filePath;
    private final FileProviderPrx fileProvider;
    private final long startOffset;
    private final long endOffset;
    private final BlockingQueue<Datagram> queue;
    private final int remoteReadSizeBytes;
    private final boolean verbose;
    private volatile RuntimeException failure;

    public static final Datagram POISON_PILL = new Datagram(true);

    public DatagramReader(String filePath, long startOffset, long endOffset, BlockingQueue<Datagram> queue) {
        this.filePath = filePath;
        this.fileProvider = null;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.queue = queue;
        this.remoteReadSizeBytes = DEFAULT_REMOTE_READ_SIZE;
        this.verbose = true;
    }

    public DatagramReader(FileProviderPrx fileProvider, long startOffset, long endOffset, BlockingQueue<Datagram> queue) {
        this(fileProvider, startOffset, endOffset, queue, DEFAULT_REMOTE_READ_SIZE, true);
    }

    public DatagramReader(
            FileProviderPrx fileProvider,
            long startOffset,
            long endOffset,
            BlockingQueue<Datagram> queue,
            int remoteReadSizeBytes,
            boolean verbose
    ) {
        this.filePath = null;
        this.fileProvider = fileProvider;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.queue = queue;
        this.remoteReadSizeBytes = Math.max(64 * 1024, remoteReadSizeBytes);
        this.verbose = verbose;
    }

    public BlockingQueue<Datagram> getQueue() {
        return queue;
    }

    public RuntimeException getFailure() {
        return failure;
    }

    @Override
    public void run() {
        if (verbose) {
            System.out.println("[Reader] Iniciando lectura de rango: [" + startOffset + " - " + endOffset + "]");
        }

        int linesRead = 0;
        int linesSkipped = 0;
        int parseErrorsLogged = 0;
        long lastLineOffset = startOffset;
        long readStartTime = System.currentTimeMillis();

        try (RangeSource source = openRangeSource()) {
            long currentFilePointer = startOffset;

            if (startOffset > 0) {
                currentFilePointer = skipPartialLine(source, currentFilePointer);
            }

            StringBuilder lineBuilder = new StringBuilder(256);
            long lineStartOffset = currentFilePointer;

            while (currentFilePointer < endOffset) {
                long blockOffsetInFile = currentFilePointer;
                int bytesToRead = (int) Math.min(source.blockSize(), endOffset - blockOffsetInFile);
                if (bytesToRead <= 0) {
                    break;
                }

                byte[] buffer = source.readBlock(blockOffsetInFile, bytesToRead);
                int bytesRead = buffer.length;
                if (bytesRead == 0) {
                    System.err.println("[Reader][WARN] Lectura retorno 0 bytes antes de endOffset"
                            + " | blockOffset=" + blockOffsetInFile
                            + " | requested=" + bytesToRead
                            + " | endOffset=" + endOffset);
                    break;
                }

                for (int i = 0; i < bytesRead; i++) {
                    char c = (char) (buffer[i] & 0xFF);
                    if (c == '\n') {
                        String line = lineBuilder.toString();
                        lineBuilder.setLength(0);

                        if (!line.isBlank()) {
                            try {
                                Datagram datagram = new Datagram(line);
                                datagram.setSourceOffset(lineStartOffset);
                                queue.put(datagram);
                                linesRead++;
                            } catch (IllegalArgumentException e) {
                                linesSkipped++;
                                if (parseErrorsLogged < 3) {
                                    System.err.println("[Reader][WARN] Linea CSV omitida"
                                            + " | lineOffset=" + lineStartOffset
                                            + " | reason=" + ErrorLog.describe(e)
                                            + " | sample=" + sample(line));
                                    parseErrorsLogged++;
                                }
                            }
                        } else {
                            linesSkipped++;
                        }

                        lastLineOffset = lineStartOffset;
                        lineStartOffset = blockOffsetInFile + i + 1;
                    } else if (c != '\r') {
                        lineBuilder.append(c);
                    }
                }

                currentFilePointer += bytesRead;
            }

            long readTime = Math.max(1, System.currentTimeMillis() - readStartTime);
            if (verbose) {
                System.out.printf("[Reader] Completado en %d ms | Lineas validas: %,d | Saltadas: %,d%n",
                        readTime,
                        linesRead,
                        linesSkipped);
            }
        } catch (Exception e) {
            failure = new RuntimeException("DatagramReader fallo leyendo rango [" + startOffset + "-" + endOffset + "]", e);
            System.err.println("[Reader][ERROR] Lectura de chunk fallo"
                    + " | range=[" + startOffset + "-" + endOffset + "]"
                    + " | remoteReadSizeBytes=" + remoteReadSizeBytes
                    + " | linesRead=" + linesRead
                    + " | linesSkipped=" + linesSkipped
                    + " | lastLineOffset=" + lastLineOffset
                    + " | error=" + ErrorLog.describe(e)
                    + " | at=" + ErrorLog.topStack(e));
        } finally {
            sendPoisonPillAndExit();
        }
    }

    private long skipPartialLine(RangeSource source, long currentOffset) throws Exception {
        long cursor = currentOffset;
        int blockSize = Math.min(source.blockSize(), 64 * 1024);

        while (cursor < endOffset) {
            int size = (int) Math.min(blockSize, endOffset - cursor);
            byte[] data = source.readBlock(cursor, size);
            if (data.length == 0) {
                return cursor;
            }

            for (int i = 0; i < data.length; i++) {
                if (data[i] == '\n') {
                    return cursor + i + 1;
                }
            }

            cursor += data.length;
        }

        return cursor;
    }

    private RangeSource openRangeSource() throws Exception {
        if (fileProvider != null) {
            return new RemoteRangeSource(fileProvider);
        }

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new IllegalArgumentException("Archivo inaccesible: " + filePath);
        }
        return new LocalRangeSource(file);
    }

    private void sendPoisonPillAndExit() {
        try {
            queue.put(POISON_PILL);
            if (verbose) {
                System.out.println("[Reader] POISON_PILL enviada. Finalizando Reader.");
            }
        } catch (InterruptedException e) {
            System.err.println("[Reader] Interrumpido al enviar POISON_PILL");
            Thread.currentThread().interrupt();
        }
    }

    private String sample(String line) {
        int max = Math.min(120, line.length());
        return line.substring(0, max).replace('\n', ' ').replace('\r', ' ');
    }

    private interface RangeSource extends AutoCloseable {
        int blockSize();
        byte[] readBlock(long offset, int size) throws Exception;
        @Override
        void close() throws Exception;
    }

    private static class LocalRangeSource implements RangeSource {
        private final RandomAccessFile raf;

        LocalRangeSource(File file) throws Exception {
            this.raf = new RandomAccessFile(file, "r");
        }

        @Override
        public int blockSize() {
            return LOCAL_BUFFER_SIZE;
        }

        @Override
        public byte[] readBlock(long offset, int size) throws Exception {
            byte[] buffer = new byte[size];
            raf.seek(offset);
            int read = raf.read(buffer, 0, size);
            if (read <= 0) {
                return new byte[0];
            }
            if (read == size) {
                return buffer;
            }

            byte[] trimmed = new byte[read];
            System.arraycopy(buffer, 0, trimmed, 0, read);
            return trimmed;
        }

        @Override
        public void close() throws Exception {
            raf.close();
        }
    }

    private class RemoteRangeSource implements RangeSource {
        private final FileProviderPrx fileProvider;

        RemoteRangeSource(FileProviderPrx fileProvider) {
            this.fileProvider = fileProvider;
        }

        @Override
        public int blockSize() {
            return remoteReadSizeBytes;
        }

        @Override
        public byte[] readBlock(long offset, int size) {
            try {
                return fileProvider.readChunk(offset, size);
            } catch (RuntimeException e) {
                System.err.println("[Reader][ERROR] readChunk remoto fallo"
                        + " | offset=" + offset
                        + " | size=" + size
                        + " | configuredBlockSize=" + remoteReadSizeBytes
                        + " | error=" + ErrorLog.describe(e)
                        + " | at=" + ErrorLog.topStack(e));
                throw e;
            }
        }

        @Override
        public void close() {
        }
    }
}
