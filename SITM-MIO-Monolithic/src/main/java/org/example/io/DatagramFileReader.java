package org.example.io;

import org.example.model.Datagram;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Sequential, single-threaded reader that iterates over every line of a
 * datagram CSV file and emits parsed {@link Datagram} objects to a consumer.
 *
 * <p>Invalid lines (malformed CSV, missing columns, unparseable numbers) are
 * silently skipped and counted.
 */
public class DatagramFileReader {

    private final String filePath;

    public DatagramFileReader(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Reads the entire file sequentially and calls {@code consumer} for each
     * valid datagram.
     *
     * @param consumer called once per valid datagram
     * @return reading statistics
     * @throws IOException if the file cannot be opened or read
     */
    public ReadStats read(Consumer<Datagram> consumer) throws IOException {
        int validLines   = 0;
        int skippedLines = 0;

        long startMs = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8),
                256 * 1024)) {

            String line;
            long lastReport = System.currentTimeMillis();
            int  lastValid  = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    skippedLines++;
                    continue;
                }
                try {
                    consumer.accept(new Datagram(line));
                    validLines++;

                    long now = System.currentTimeMillis();
                    if (now - lastReport >= 2_000) {
                        int rate = (int) ((validLines - lastValid) / 2.0);
                        System.out.printf("[Reader] Leidas: %,8d | Omitidas: %,6d | Rate: %,6d/s%n",
                                validLines, skippedLines, rate);
                        lastReport = now;
                        lastValid  = validLines;
                    }
                } catch (IllegalArgumentException ignored) {
                    skippedLines++;
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        return new ReadStats(validLines, skippedLines, elapsed);
    }

    // -----------------------------------------------------------------------

    /** Statistics produced by one full-file read. */
    public static final class ReadStats {
        public final int  validLines;
        public final int  skippedLines;
        public final long elapsedMs;

        ReadStats(int validLines, int skippedLines, long elapsedMs) {
            this.validLines   = validLines;
            this.skippedLines = skippedLines;
            this.elapsedMs    = elapsedMs;
        }
    }
}
