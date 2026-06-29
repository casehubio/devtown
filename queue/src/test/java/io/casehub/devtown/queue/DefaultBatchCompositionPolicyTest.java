package io.casehub.devtown.queue;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.devtown.domain.queue.PriorityLane;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultBatchCompositionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");
    private static final double DECAY_RATE = 125.0;

    private final DefaultBatchCompositionPolicy policy = new DefaultBatchCompositionPolicy();

    private QueuedPr pr(int number, double trust) {
        return new QueuedPr(number, "casehubio/devtown", "sha" + number, "author" + number, trust,
                            PriorityLane.NORMAL, NOW, Set.of());
    }

    private QueuedPr pr(int number, double trust, Set<Integer> deps) {
        return new QueuedPr(number, "casehubio/devtown", "sha" + number, "author" + number, trust,
                            PriorityLane.NORMAL, NOW, deps);
    }

    private BatchFormationContext ctx(int maxBatch, int minBatch, double failureRate) {
        return new BatchFormationContext(NOW, maxBatch, minBatch, DECAY_RATE, failureRate,
                                         "casehubio/devtown", "main", "normal", "trust-weighted",
                                         new AtomicInteger(0));
    }

    @Test
    void emptyQueueReturnsEmptyList() {
        var result = policy.formBatches(List.of(), ctx(10, 1, 0.0));
        assertThat(result).isEmpty();
    }

    @Test
    void allPrsFormOneBatchWhenWithinEffectiveMax() {
        // 3 PRs with trust 0.8, maxBatch=10 → trustMax=floor(0.8*10)=8
        // failureRate=0 → adaptiveMax=max(1,floor(8*1.0))=8
        // 3 < 8 → all in one batch
        var prs = List.of(pr(1, 0.8), pr(2, 0.8), pr(3, 0.8));
        var result = policy.formBatches(prs, ctx(10, 1, 0.0));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prs()).hasSize(3);
    }

    @Test
    void trustWeightedSizing_lowTrustReducesBatchSize() {
        // maxBatch=10, minBatch=1, failureRate=0
        // PR with trust 0.3 → trustMax = floor(0.3*10) = 3
        // adaptiveMax = max(1, floor(3*1.0)) = 3
        // 5 PRs should split into batches of <=3
        var prs = List.of(
            pr(1, 0.3), pr(2, 0.3), pr(3, 0.3), pr(4, 0.3), pr(5, 0.3)
        );
        var result = policy.formBatches(prs, ctx(10, 1, 0.0));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prs()).hasSize(3);
        assertThat(result.get(1).prs()).hasSize(2);
    }

    @Test
    void adaptiveSizing_failureRateReducesBatchSize() {
        // maxBatch=10, minBatch=1, failureRate=0.5
        // All trust 1.0 → trustMax = floor(1.0*10) = 10
        // adaptiveMax = max(1, floor(10*0.5)) = 5
        var prs = List.of(
            pr(1, 1.0), pr(2, 1.0), pr(3, 1.0), pr(4, 1.0), pr(5, 1.0),
            pr(6, 1.0), pr(7, 1.0)
        );
        var result = policy.formBatches(prs, ctx(10, 1, 0.5));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prs()).hasSize(5);
        assertThat(result.get(1).prs()).hasSize(2);
    }

    @Test
    void minBatchSizeFloor_preventsDegenerate() {
        // maxBatch=10, minBatch=3, failureRate=0.9
        // trust 0.5 → trustMax = floor(0.5*10) = 5
        // adaptiveMax = max(3, floor(5*0.1)) = max(3, 0) = 3
        var prs = List.of(pr(1, 0.5), pr(2, 0.5), pr(3, 0.5), pr(4, 0.5));
        var result = policy.formBatches(prs, ctx(10, 3, 0.9));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prs()).hasSize(3);
        assertThat(result.get(1).prs()).hasSize(1);
    }

    @Test
    void dependencyOrdering_dependentNeverBeforeDependency() {
        // PR 2 depends on PR 1. Even if PR 2 has higher priority,
        // PR 1 must appear first within its batch.
        var pr1 = pr(1, 0.5);
        var pr2 = pr(2, 0.9, Set.of(1));
        var result = policy.formBatches(List.of(pr2, pr1), ctx(10, 1, 0.0));

        assertThat(result).hasSize(1);
        var batch = result.get(0);
        var numbers = batch.prs().stream().map(QueuedPr::number).toList();
        assertThat(numbers.indexOf(1)).isLessThan(numbers.indexOf(2));
    }

    @Test
    void batchMetadataIsPopulated() {
        var prs = List.of(pr(1, 0.8));
        var result = policy.formBatches(prs, ctx(10, 1, 0.0));

        assertThat(result).hasSize(1);
        var batch = result.get(0);
        assertThat(batch.id()).startsWith("batch-");
        assertThat(batch.targetBranch()).isEqualTo("main");
        assertThat(batch.riskLevel()).isEqualTo("normal");
        assertThat(batch.bisectionStrategy()).isEqualTo("trust-weighted");
    }

    @Test
    void multipleBatchesGetSequentialIds() {
        var prs = List.of(pr(1, 0.3), pr(2, 0.3), pr(3, 0.3), pr(4, 0.3));
        var result = policy.formBatches(prs, ctx(10, 1, 0.0));

        assertThat(result).hasSizeGreaterThan(1);
        var ids = result.stream().map(Batch::id).toList();
        // All IDs unique
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void mixedTrustScores_batchSizeDrivenByMinTrustInBatch() {
        // maxBatch=10, failureRate=0
        // Priority sort: higher priority first. All same lane/enqueue time, so trust breaks ties.
        // Sorted by score desc: PR1(1.0), PR3(0.8), PR5(0.7), PR4(0.6), PR2(0.2)
        // After DependencyResolver (no deps), same order.
        // Building batch:
        //   +PR1: minTrust=1.0, trustMax=10, adaptiveMax=10 → 1<10 continue
        //   +PR3: minTrust=0.8, trustMax=8, adaptiveMax=8 → 2<8 continue
        //   +PR5: minTrust=0.7, trustMax=7, adaptiveMax=7 → 3<7 continue
        //   +PR4: minTrust=0.6, trustMax=6, adaptiveMax=6 → 4<6 continue
        //   +PR2: minTrust=0.2, trustMax=2, adaptiveMax=2 → 5>=2 → close batch(5)
        // Result: 1 batch of 5
        // With 7 PRs we can force a second batch.
        var prs = List.of(
            pr(1, 1.0), pr(2, 0.2), pr(3, 0.8), pr(4, 0.6), pr(5, 0.7),
            pr(6, 0.9), pr(7, 0.5)
        );
        var result = policy.formBatches(prs, ctx(10, 1, 0.0));

        // First batch closes when size >= adaptiveMax after low-trust PR enters
        assertThat(result).hasSizeGreaterThan(1);
        // Total PRs across all batches equals input
        int totalPrs = result.stream().mapToInt(Batch::size).sum();
        assertThat(totalPrs).isEqualTo(7);
    }
}
