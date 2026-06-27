package io.casehub.devtown.queue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public record BatchFormationContext(
    Instant now,
    int maxBatchSize,
    int minBatchSize,
    double decayRatePerHour,
    double recentFailureRate,
    String targetBranch,
    String riskLevel,
    String bisectionStrategy,
    AtomicInteger batchSequence
) {
    public BatchFormationContext {
        if (maxBatchSize < 1) throw new IllegalArgumentException("maxBatchSize must be >= 1");
        if (minBatchSize < 1) throw new IllegalArgumentException("minBatchSize must be >= 1");
        if (recentFailureRate < 0.0 || recentFailureRate > 1.0)
            throw new IllegalArgumentException("recentFailureRate must be in [0.0, 1.0]");
    }

    public int nextBatchSequence() {
        return batchSequence.getAndIncrement();
    }
}
