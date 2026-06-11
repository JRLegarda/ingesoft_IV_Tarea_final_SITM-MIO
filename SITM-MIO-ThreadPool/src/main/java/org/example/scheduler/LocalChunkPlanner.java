package org.example.scheduler;

import org.example.core.ChunkProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Divides a file into byte-range chunks suitable for parallel processing.
 *
 * <p>Each chunk has:
 * <ul>
 *   <li>{@code calculationStart} — the logical start of this chunk; only
 *       datagrams at or after this offset are counted.</li>
 *   <li>{@code readStart} — {@code max(0, calculationStart - overlapBytes)};
 *       reading starts here so the processor has prior bus-position context.</li>
 *   <li>{@code readEnd} — {@code min(calculationStart + chunkSize, fileSize)}.</li>
 * </ul>
 *
 * <p>Default values (configurable via constructor):
 * <ul>
 *   <li>Chunk size: 10 MB</li>
 *   <li>Overlap:    1 MB</li>
 * </ul>
 */
public class LocalChunkPlanner {

    /** Default chunk size: 10 MB */
    public static final long DEFAULT_CHUNK_SIZE    = 10L * 1024 * 1024;
    /** Default overlap:  1 MB */
    public static final long DEFAULT_OVERLAP_BYTES =  1L * 1024 * 1024;

    private final long chunkSize;
    private final long overlapBytes;

    public LocalChunkPlanner() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_BYTES);
    }

    public LocalChunkPlanner(long chunkSize, long overlapBytes) {
        this.chunkSize    = chunkSize;
        this.overlapBytes = Math.max(0, overlapBytes);
    }

    /**
     * Produces a list of {@link ChunkProcessor} tasks covering the entire
     * file of {@code fileSize} bytes.
     *
     * @param filePath  path to the datagram CSV
     * @param fileSize  total byte length of the file
     * @return ordered list of chunk tasks, ready for submission to a thread pool
     */
    public List<ChunkProcessor> plan(String filePath, long fileSize) {
        List<ChunkProcessor> tasks = new ArrayList<>();
        long currentOffset = 0L;
        int  chunkIndex    = 1;

        while (currentOffset < fileSize) {
            long calculationStart = currentOffset;
            long readStart        = Math.max(0L, calculationStart - overlapBytes);
            long readEnd          = Math.min(calculationStart + chunkSize, fileSize);

            tasks.add(new ChunkProcessor(filePath, readStart, readEnd, calculationStart, chunkIndex));
            chunkIndex++;
            currentOffset = readEnd;
        }

        return tasks;
    }

    public long getChunkSize()    { return chunkSize; }
    public long getOverlapBytes() { return overlapBytes; }
}
