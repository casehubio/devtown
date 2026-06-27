package io.casehub.devtown.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BisectionSplitStrategyTest {

    private static Map<String, Object> prMap(int number, String sha, String author, double trust) {
        return Map.of("number", number, "headSha", sha, "author", author, "trustScore", trust);
    }

    private static final String BATCH_ID = "batch-1";
    private static final String TARGET = "main";
    private static final String STRATEGY = "trust-weighted";
    private static final String RISK = "normal";

    @Nested
    class TrustWeighted {

        private final TrustWeightedSplitStrategy strategy = new TrustWeightedSplitStrategy();

        @Test
        void lowTrustPrsClusterInLeftHalf() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.3),
                prMap(3, "c", "carol", 0.8),
                prMap(4, "d", "dave", 0.2)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // Left half should contain the two lowest trust PRs (0.2 and 0.3)
            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);

            var leftTrusts = result.left().prs().stream()
                .mapToDouble(m -> (double) m.get("trustScore")).toArray();
            var rightTrusts = result.right().prs().stream()
                .mapToDouble(m -> (double) m.get("trustScore")).toArray();

            // Every left trust should be <= every right trust
            for (double lt : leftTrusts) {
                for (double rt : rightTrusts) {
                    assertThat(lt).isLessThanOrEqualTo(rt);
                }
            }
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.3)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(prMap(1, "a", "alice", 0.5));

            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void oddSizedBatchSplitsWithLargerRightHalf() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.5),
                prMap(3, "c", "carol", 0.2)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // midpoint = 3/2 = 1, so left=1, right=2
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(2);
        }

        @Test
        void sliceMetadataIsCorrect() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.3)
            );

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
            // Scores: 0.8, 0.85, 0.82, 0.05
            // Mean = 0.63, stddev = 0.338, threshold = 0.63 - 2*0.338 = -0.046
            // That's too low. Use tighter cluster with extreme outlier:
            // Scores: 0.80, 0.85, 0.82, 0.01
            // Mean = 0.62, variance includes extreme pull from 0.01
            // Better approach: use 5 tight scores + 1 extreme outlier
            var prs = List.of(
                prMap(1, "a", "alice", 0.80),
                prMap(2, "b", "bob", 0.82),
                prMap(3, "c", "carol", 0.81),
                prMap(4, "d", "dave", 0.79),
                prMap(5, "e", "eve", 0.83),
                prMap(6, "f", "frank", 0.05)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // The outlier (0.05) should be isolated as a solo left batch
            assertThat(result.left().prs()).hasSize(1);
            assertThat((double) result.left().prs().get(0).get("trustScore")).isEqualTo(0.05);
            assertThat(result.right().prs()).hasSize(5);
        }

        @Test
        void noOutlierDelegatesToTrustWeightedSplit() {
            // All trust scores close together — no outlier
            var prs = List.of(
                prMap(1, "a", "alice", 0.7),
                prMap(2, "b", "bob", 0.8),
                prMap(3, "c", "carol", 0.75),
                prMap(4, "d", "dave", 0.72)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // Should fall back to trust-weighted: 2 left, 2 right
            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.1)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(prMap(1, "a", "alice", 0.5));

            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Binary {

        private final BinarySplitStrategy strategy = new BinarySplitStrategy();

        @Test
        void splitsAtPositionalMidpointRegardlessOfTrust() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.2),
                prMap(2, "b", "bob", 0.9),
                prMap(3, "c", "carol", 0.1),
                prMap(4, "d", "dave", 0.8)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // Positional midpoint: 4/2 = 2, left gets [0,2), right gets [2,4)
            assertThat(result.left().prs()).hasSize(2);
            assertThat(result.right().prs()).hasSize(2);

            // Order preserved — no sorting by trust
            assertThat((int) result.left().prs().get(0).get("number")).isEqualTo(1);
            assertThat((int) result.left().prs().get(1).get("number")).isEqualTo(2);
            assertThat((int) result.right().prs().get(0).get("number")).isEqualTo(3);
            assertThat((int) result.right().prs().get(1).get("number")).isEqualTo(4);
        }

        @Test
        void batchOfTwoProducesTwoSingletons() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.9),
                prMap(2, "b", "bob", 0.3)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(1);
        }

        @Test
        void batchOfOneThrows() {
            var prs = List.of(prMap(1, "a", "alice", 0.5));

            assertThatThrownBy(() -> strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void oddSizedBatchSplitsLeftSmallerRightLarger() {
            var prs = List.of(
                prMap(1, "a", "alice", 0.5),
                prMap(2, "b", "bob", 0.5),
                prMap(3, "c", "carol", 0.5)
            );

            var result = strategy.split(prs, BATCH_ID, TARGET, 1, STRATEGY, RISK);

            // midpoint = 3/2 = 1
            assertThat(result.left().prs()).hasSize(1);
            assertThat(result.right().prs()).hasSize(2);
        }
    }
}
