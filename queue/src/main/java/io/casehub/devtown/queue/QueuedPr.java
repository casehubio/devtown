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
        Set<Integer> dependsOn,
        double riskScore
) {
    public QueuedPr {
        Objects.requireNonNull(repository, "repository must not be null");
        if (trustScore < 0.0 || trustScore > 1.0) {
            throw new IllegalArgumentException("trustScore must be in [0.0, 1.0]: " + trustScore);
        }
        if (riskScore < 0.0 || riskScore > 1.0) {
            throw new IllegalArgumentException("riskScore must be in [0.0, 1.0]: " + riskScore);
        }
    }

    public QueuedPr(int number, String repository, String headSha, String author,
                    double trustScore, PriorityLane lane, Instant enqueuedAt, Set<Integer> dependsOn) {
        this(number, repository, headSha, author, trustScore, lane, enqueuedAt, dependsOn, 0.0);
    }

    public QueuedPr withRiskScore(double riskScore) {
        return new QueuedPr(number, repository, headSha, author, trustScore, lane, enqueuedAt, dependsOn, riskScore);
    }
}
