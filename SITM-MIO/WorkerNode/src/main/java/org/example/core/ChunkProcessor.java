package org.example.core;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import org.example.engine.DatagramReader;
import org.example.engine.SpeedCalculator;
import org.example.model.Datagram;

import Demo.FileProviderPrx;

public class ChunkProcessor {

    public ChunkResult process(String filePath, long startOffset, long endOffset, long calculationStartOffset) {
        return process(new DatagramReader(filePath, startOffset, endOffset, newQueue()), calculationStartOffset);
    }

    public ChunkResult process(FileProviderPrx fileProvider, long startOffset, long endOffset, long calculationStartOffset) {
        return process(fileProvider, startOffset, endOffset, calculationStartOffset, DatagramReader.DEFAULT_REMOTE_READ_SIZE, true);
    }

    public ChunkResult process(
            FileProviderPrx fileProvider,
            long startOffset,
            long endOffset,
            long calculationStartOffset,
            int remoteReadSizeBytes,
            boolean verbose
    ) {
        return process(new DatagramReader(fileProvider, startOffset, endOffset, newQueue(), remoteReadSizeBytes, verbose), calculationStartOffset);
    }

    private ChunkResult process(DatagramReader reader, long calculationStartOffset) {
        long start = System.currentTimeMillis();

        BlockingQueue<Datagram> queue = reader.getQueue();
        SpeedCalculator calculator = new SpeedCalculator(queue, calculationStartOffset);

        Thread calculatorThread = new Thread(calculator, "Calculator-Thread");
        Thread readerThread = new Thread(reader, "Reader-Thread");

        calculatorThread.start();
        readerThread.start();

        try {
            readerThread.join();
            calculatorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (reader.getFailure() != null) {
            throw reader.getFailure();
        }

        long time = System.currentTimeMillis() - start;
        return new ChunkResult(
                resolveWorkerName(),
                time,
                calculator.processed,
                calculator.speedCalculated,
                routeMonthResults(calculator)
        );
    }

    private BlockingQueue<Datagram> newQueue() {
        return new ArrayBlockingQueue<>(20000);
    }

    private List<RouteMonthResult> routeMonthResults(SpeedCalculator calculator) {
        return calculator.getRouteMonthSpeedMap().entrySet().stream()
                .map(entry -> new RouteMonthResult(
                    entry.getKey().getLineId(),
                    formatYearMonth(entry.getKey().getMonth()),
                    entry.getValue().getTotalSpeed(),
                    entry.getValue().getCount()
                ))
                .collect(Collectors.toList());
    }

    private String formatYearMonth(int yearMonth) {
        int year = yearMonth / 100;
        int month = yearMonth % 100;
        return year + "-" + (month < 10 ? "0" : "") + month;
    }

    private String resolveWorkerName() {
        String workerName = System.getProperty("user.name");
        return workerName == null ? "Unknown" : workerName;
    }
}
