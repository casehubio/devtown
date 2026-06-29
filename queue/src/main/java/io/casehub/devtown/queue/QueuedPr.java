package io.casehub.devtown.queue;

import io.casehub.devtown.domain.queue.PriorityLane;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record QueuedPr(
    int number,
    String repository,
    String headSha,
    String author,
    double trustScore,
    PriorityLane lane,
    Instant enqueuedAt,
    Set<Integer> dependsOn
) {
    public QueuedPr {
        Objects.requireNonNull(repository, "repository must not be null");
        if (trustScore < 0.0 || trustScore > 1.0)
            throw new IllegalArgumentException("trustScore must be in [0.0, 1.0]: " + trustScore);
    }
}
