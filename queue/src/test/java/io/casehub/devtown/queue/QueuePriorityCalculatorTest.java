package io.casehub.devtown.queue;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.devtown.domain.queue.PriorityLane;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueuePriorityCalculatorTest {

    private static final double DECAY_RATE = 125.0;
    private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");

    private QueuedPr pr(PriorityLane lane, double trust, Instant enqueued) {
        return new QueuedPr(1, "casehubio/devtown", "abc", "alice", trust, lane, enqueued, Set.of());
    }

    @Test
    void criticalBeatsHighBeatsNormal() {
        var critical = pr(PriorityLane.CRITICAL, 0.5, NOW);
        var high     = pr(PriorityLane.HIGH, 0.5, NOW);
        var normal   = pr(PriorityLane.NORMAL, 0.5, NOW);

        assertThat(QueuePriorityCalculator.score(critical, NOW, DECAY_RATE))
            .isGreaterThan(QueuePriorityCalculator.score(high, NOW, DECAY_RATE));
        assertThat(QueuePriorityCalculator.score(high, NOW, DECAY_RATE))
            .isGreaterThan(QueuePriorityCalculator.score(normal, NOW, DECAY_RATE));
    }

    @Test
    void higherTrustScoresHigherWithinSameLane() {
        var highTrust = pr(PriorityLane.NORMAL, 0.9, NOW);
        var lowTrust  = pr(PriorityLane.NORMAL, 0.3, NOW);

        assertThat(QueuePriorityCalculator.score(highTrust, NOW, DECAY_RATE))
            .isGreaterThan(QueuePriorityCalculator.score(lowTrust, NOW, DECAY_RATE));
    }

    @Test
    void starvationPrevention_normalOvertakesFreshHighAfterEightHours() {
        var staleNormal = pr(PriorityLane.NORMAL, 0.5, NOW.minus(8, ChronoUnit.HOURS).minus(1, ChronoUnit.MINUTES));
        var freshHigh   = pr(PriorityLane.HIGH, 0.5, NOW);

        assertThat(QueuePriorityCalculator.score(staleNormal, NOW, DECAY_RATE))
            .isGreaterThan(QueuePriorityCalculator.score(freshHigh, NOW, DECAY_RATE));
    }

    @Test
    void noDecayAtEnqueueTime() {
        var pr = pr(PriorityLane.NORMAL, 0.5, NOW);
        double score = QueuePriorityCalculator.score(pr, NOW, DECAY_RATE);
        double expected = 1.0 * 1000 + 0.5 * 100 + 0.0;
        assertThat(score).isEqualTo(expected);
    }
}
