package io.casehub.devtown.queue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BisectionSplitStrategyTest {

    private static QueuedPr pr(int number, String sha, String author, double trust) {
        return new QueuedPr(number, "casehubio/devtown", sha, author, trust,
                            io.casehub.devtown.domain.queue.PriorityLane.NORMAL,
                            java.time.Instant.parse("2026-07-14T12:00:00Z"), java.util.Set.of());
    }

    private static final String BATCH_ID = "batch-1";
    private static final String TARGET   = "main";
    private static final String STRATEGY = "trust-weighted";
    private static final String RISK     = "normal";

    @Nested
    class TrustWeighted {

        private final TrustWeightedSplitStrategy strategy = new TrustWeightedSplitStrategy();

        @Test
        void lowTrustPrsClusterInLeftHalf() {
            var prs = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.3),
                              pr(3, "c", "carol", 0.8), pr(4, "d", "dave", 0.2));

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);

            var leftTrusts  = result.left().prs().stream().mapToDouble(QueuedPr::trustScore).toArray();
            var rightTrusts = result.right().prs().stream().mapToDouble(QueuedPr::trustScore).toArray();

            for (double lt : leftTrusts) {
                for (double rt : rightTrusts) {
                    assertThat(lt).isLessThanOrEqualTo(rt);
                }
            }
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs    = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.3));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(pr(1, "a", "alice", 0.5));
            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void oddSizedBatchSplitsWithLargerRightHalf() {
            var prs    = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.5), pr(3, "c", "carol", 0.2));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(2);
        }

        @Test
        void sliceMetadataIsCorrect() {
            var prs    = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.3));
            var result = strategy.split(prs, BATCH_ID, TARGET, 2, STRATEGY, RISK);

            assertThat(result.left().parentBatchId()).isEqualTo(BATCH_ID);
            assertThat(result.left().targetBranch()).isEqualTo(TARGET);
            assertThat(result.left().bisectionDepth()).isEqualTo(2);
            assertThat(result.left().bisectionStrategy()).isEqualTo(STRATEGY);
            assertThat(result.left().riskLevel()).isEqualTo(RISK);
            assertThat(result.left().size()).isEqualTo(1);
            assertThat(result.right().parentBatchId()).isEqualTo(BATCH_ID);
            assertThat(result.right().size()).isEqualTo(1);
        }
    }

    @Nested
    class IsolateOutlier {

        private final IsolateOutlierStrategy strategy = new IsolateOutlierStrategy();

        @Test
        void outlierMoreThanTwoSigmaBelowMeanIsIsolated() {
            var prs = List.of(
                    pr(1, "a", "alice", 0.80), pr(2, "b", "bob", 0.82),
                    pr(3, "c", "carol", 0.81), pr(4, "d", "dave", 0.79),
                    pr(5, "e", "eve", 0.83), pr(6, "f", "frank", 0.05));

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.left().prs().get(0).trustScore()).isEqualTo(0.05);
            assertThat(result.right().prs()).hasSize(5);
        }

        @Test
        void noOutlierDelegatesToTrustWeightedSplit() {
            var prs = List.of(pr(1, "a", "alice", 0.7), pr(2, "b", "bob", 0.8),
                              pr(3, "c", "carol", 0.75), pr(4, "d", "dave", 0.72));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs    = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.1));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(pr(1, "a", "alice", 0.5));
            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Binary {

        private final BinarySplitStrategy strategy = new BinarySplitStrategy();

        @Test
        void splitsAtPositionalMidpointRegardlessOfTrust() {
            var prs = List.of(pr(1, "a", "alice", 0.2), pr(2, "b", "bob", 0.9),
                              pr(3, "c", "carol", 0.1), pr(4, "d", "dave", 0.8));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);
            assertThat(result.left().prs().get(0).number()).isEqualTo(1);
            assertThat(result.left().prs().get(1).number()).isEqualTo(2);
            assertThat(result.right().prs().get(0).number()).isEqualTo(3);
            assertThat(result.right().prs().get(1).number()).isEqualTo(4);
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs    = List.of(pr(1, "a", "alice", 0.9), pr(2, "b", "bob", 0.3));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(pr(1, "a", "alice", 0.5));
            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void oddSizedBatchSplitsLeftSmallerRightLarger() {
            var prs    = List.of(pr(1, "a", "alice", 0.5), pr(2, "b", "bob", 0.5), pr(3, "c", "carol", 0.5));
            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(2);
        }
    }
}
