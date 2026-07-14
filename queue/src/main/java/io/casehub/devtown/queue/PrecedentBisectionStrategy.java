package io.casehub.devtown.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PrecedentBisectionStrategy implements BisectionSplitStrategy {

    private final BinarySplitStrategy fallback = new BinarySplitStrategy();

    @Override
    public SplitResult split(List<QueuedPr> prs, String batchId,
                              String targetBranch, int bisectionDepth,
                              String bisectionStrategy, String riskLevel) {
        if (prs.size() < 2) {
            throw new IllegalArgumentException("Cannot split a batch with fewer than 2 PRs");
        }

        boolean hasRiskData = prs.stream().anyMatch(p -> p.riskScore() > 0.0);
        if (!hasRiskData) {
            return fallback.split(prs, batchId, targetBranch, bisectionDepth,
                                  bisectionStrategy, riskLevel);
        }

        List<QueuedPr> sorted = new ArrayList<>(prs);
        sorted.sort(Comparator.comparingDouble(QueuedPr::riskScore).reversed());

        int midpoint = sorted.size() / 2;
        List<QueuedPr> leftPrs = List.copyOf(sorted.subList(0, midpoint));
        List<QueuedPr> rightPrs = List.copyOf(sorted.subList(midpoint, sorted.size()));

        var left = new BatchSlice(
            batchId + "-L", targetBranch, leftPrs, leftPrs.size(),
            batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        var right = new BatchSlice(
            batchId + "-R", targetBranch, rightPrs, rightPrs.size(),
            batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        return new SplitResult(left, right);
    }
}
