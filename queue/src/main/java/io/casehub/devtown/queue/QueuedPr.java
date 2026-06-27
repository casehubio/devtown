package io.casehub.devtown.queue;

import java.time.Instant;
import java.util.Set;

public record QueuedPr(
    int number,
    String headSha,
    String author,
    double trustScore,
    PriorityLane lane,
    Instant enqueuedAt,
    Set<Integer> dependsOn
) {
    public QueuedPr {
        if (trustScore < 0.0 || trustScore > 1.0)
            throw new IllegalArgumentException("trustScore must be in [0.0, 1.0]: " + trustScore);
    }
}
