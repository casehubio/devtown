package io.casehub.devtown.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DefaultBatchCompositionPolicy implements BatchCompositionPolicy {

    @Override
    public List<Batch> formBatches(List<QueuedPr> queue, BatchFormationContext ctx) {
        if (queue.isEmpty()) return List.of();

        List<QueuedPr> sorted = queue.stream()
            .sorted(Comparator.comparingDouble(
                (QueuedPr pr) -> QueuePriorityCalculator.score(pr, ctx.now(), ctx.decayRatePerHour()))
                .reversed())
            .toList();

        List<QueuedPr> ordered = DependencyResolver.resolve(sorted);

        List<Batch> batches = new ArrayList<>();
        List<QueuedPr> currentBatch = new ArrayList<>();

        for (QueuedPr pr : ordered) {
            currentBatch.add(pr);
            double minTrust = currentBatch.stream()
                .mapToDouble(QueuedPr::trustScore).min().orElse(1.0);
            int trustMax = Math.max(1, (int) Math.floor(minTrust * ctx.maxBatchSize()));
            int adaptiveMax = Math.max(ctx.minBatchSize(),
                (int) Math.floor(trustMax * (1.0 - ctx.recentFailureRate())));

            if (currentBatch.size() >= adaptiveMax) {
                batches.add(buildBatch(currentBatch, ctx));
                currentBatch = new ArrayList<>();
            }
        }
        if (!currentBatch.isEmpty()) {
            batches.add(buildBatch(currentBatch, ctx));
        }
        return batches;
    }

    private Batch buildBatch(List<QueuedPr> prs, BatchFormationContext ctx) {
        String id = "batch-" + ctx.now().getEpochSecond() + "-" + ctx.nextBatchSequence();
        return new Batch(id, List.copyOf(prs), ctx.repository(), ctx.targetBranch(),
                         ctx.riskLevel(), ctx.bisectionStrategy());
    }
}
