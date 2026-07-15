package io.casehub.devtown.queue;

import io.casehub.devtown.domain.queue.PriorityLane;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrecedentBisectionStrategyTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");
    private static final String BATCH_ID = "batch-1";

    private final PrecedentBisectionStrategy strategy = new PrecedentBisectionStrategy();

    private QueuedPr pr(int number, double trust, double risk) {
        return new QueuedPr(number, "casehubio/devtown", "sha" + number, "author" + number,
                            trust, PriorityLane.NORMAL, NOW, Set.of(), risk);
    }

    private QueuedPr pr(int number, double trust) {
        return new QueuedPr(number, "casehubio/devtown", "sha" + number, "author" + number,
                            trust, PriorityLane.NORMAL, NOW, Set.of());
    }

    @Test
    void withRiskData_highestRiskPrsInLeftHalf() {
        var prs = List.of(
            pr(1, 0.8, 0.2),
            pr(2, 0.8, 0.9),
            pr(3, 0.8, 0.1),
            pr(4, 0.8, 0.7)
        );

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs()).hasSize(2);
        assertThat(result.right().prs()).hasSize(2);

        var leftRisks = result.left().prs().stream().mapToDouble(QueuedPr::riskScore).toArray();
        var rightRisks = result.right().prs().stream().mapToDouble(QueuedPr::riskScore).toArray();

        for (double lr : leftRisks) {
            for (double rr : rightRisks) {
                assertThat(lr).as("left risk should be >= right risk").isGreaterThanOrEqualTo(rr);
            }
        }
    }

    @Test
    void withRiskData_bisectionStartsFromHighestRiskCandidate() {
        var prs = List.of(
            pr(1, 0.8, 0.1),
            pr(2, 0.8, 0.3),
            pr(3, 0.8, 0.95),
            pr(4, 0.8, 0.5)
        );

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs().get(0).number()).isEqualTo(3);
    }

    @Test
    void noRiskData_fallsBackToBinarySplit() {
        var prs = List.of(pr(1, 0.8), pr(2, 0.3), pr(3, 0.9), pr(4, 0.5));

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs()).hasSize(2);
        assertThat(result.right().prs()).hasSize(2);
        assertThat(result.left().prs().get(0).number()).isEqualTo(1);
        assertThat(result.left().prs().get(1).number()).isEqualTo(2);
    }

    @Test
    void allZeroRisk_fallsBackToBinarySplit() {
        var prs = List.of(pr(1, 0.8, 0.0), pr(2, 0.8, 0.0), pr(3, 0.8, 0.0));

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs()).hasSize(1);
        assertThat(result.right().prs()).hasSize(2);
        assertThat(result.left().prs().get(0).number()).isEqualTo(1);
    }

    @Test
    void oddSizedBatch_largerHalfIsLowerRisk() {
        var prs = List.of(pr(1, 0.8, 0.9), pr(2, 0.8, 0.5), pr(3, 0.8, 0.1));

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs()).hasSize(1);
        assertThat(result.right().prs()).hasSize(2);
        assertThat(result.left().prs().get(0).riskScore()).isEqualTo(0.9);
    }

    @Test
    void batchOfTwo_splitsIntoSingletons() {
        var prs = List.of(pr(1, 0.8, 0.9), pr(2, 0.8, 0.1));

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs()).hasSize(1);
        assertThat(result.right().prs()).hasSize(1);
        assertThat(result.left().prs().get(0).riskScore()).isEqualTo(0.9);
    }

    @Test
    void batchOfOne_throws() {
        var prs = List.of(pr(1, 0.8, 0.5));

        assertThatThrownBy(() -> strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sliceMetadata_isCorrect() {
        var prs = List.of(pr(1, 0.8, 0.9), pr(2, 0.8, 0.1));

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 2, "precedent", "HIGH");

        assertThat(result.left().parentBatchId()).isEqualTo(BATCH_ID);
        assertThat(result.left().targetBranch()).isEqualTo("main");
        assertThat(result.left().bisectionDepth()).isEqualTo(2);
        assertThat(result.left().bisectionStrategy()).isEqualTo("precedent");
        assertThat(result.left().riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void averageBisectionRounds_reducedWithPrecedentData() {
        var prs = List.of(
            pr(1, 0.8, 0.05),
            pr(2, 0.8, 0.02),
            pr(3, 0.8, 0.95),
            pr(4, 0.8, 0.03)
        );

        var result = strategy.split(prs, "casehubio/devtown", BATCH_ID, "main", 1, "precedent", "normal");

        assertThat(result.left().prs().get(0).number())
            .as("highest-risk PR should be in left half for earliest bisection")
            .isEqualTo(3);
    }
}
