module Demo {

    struct RouteMonthSpeed {
        int lineId;
        string month;
        double speedSum;
        int count;
    };

    sequence<RouteMonthSpeed> RouteMonthSpeedList;

    struct TaskResult {
        string workerName;
        long executionTime;
        int processedCount;
        int speedCount;
        RouteMonthSpeedList routeMonthSpeeds;
    };

    sequence<byte> ByteSeq;

    interface FileProvider {
        long fileSize();
        ByteSeq readChunk(long offset, int size);
    };

    interface Worker {
        TaskResult processDatagramLog(
            FileProvider* fileProvider,
            long startOffset,
            long endOffset,
            long calculationStartOffset,
            int remoteReadSizeBytes,
            bool verbose
        );
    };
};
