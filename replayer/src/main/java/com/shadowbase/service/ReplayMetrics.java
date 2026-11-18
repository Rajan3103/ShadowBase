package com.shadowbase.service;


import java.util.concurrent.atomic.AtomicLong;

public class ReplayMetrics {

    private final AtomicLong totalReplays = new AtomicLong();
    private final AtomicLong successfulReplays = new AtomicLong();
    private final AtomicLong failedReplays = new AtomicLong();

    public void recordSuccess() {
        totalReplays.incrementAndGet();
        successfulReplays.incrementAndGet();
    }

    public void recordFailure() {
        totalReplays.incrementAndGet();
        failedReplays.incrementAndGet();
    }

    public long getTotalReplays() {
        return totalReplays.get();
    }

    public long getSuccessfulReplays() {
        return successfulReplays.get();
    }

    public long getFailedReplays() {
        return failedReplays.get();
    }

    public double getErrorRate() {
        long total = totalReplays.get();

        if (total == 0) {
            return 0.0;
        }

        return (double) failedReplays.get() / total;
    }

    public void printMetrics() {
        System.out.println();
        System.out.println("========== Replay Metrics ==========");
        System.out.println("Total replays       : " + getTotalReplays());
        System.out.println("Successful replays  : " + getSuccessfulReplays());
        System.out.println("Failed replays      : " + getFailedReplays());
        System.out.printf("Error rate          : %.2f%%%n", getErrorRate() * 100);
        System.out.println("====================================");
    }
}
