package io.casehub.devtown.queue;

import java.time.Duration;
import java.time.Instant;

public final class QueuePriorityCalculator {

    private QueuePriorityCalculator() {}

    public static double score(QueuedPr pr, Instant now, double decayRatePerHour) {
        double lanePriority = pr.lane().weight() * 1000.0;
        double trustComponent = pr.trustScore() * 100.0;
        long waitMinutes = Duration.between(pr.enqueuedAt(), now).toMinutes();
        double waitDecay = waitMinutes * (decayRatePerHour / 60.0);
        return lanePriority + trustComponent + waitDecay;
    }
}
