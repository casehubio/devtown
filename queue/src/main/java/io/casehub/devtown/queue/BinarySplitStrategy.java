package io.casehub.devtown.queue;

import java.util.List;

public class BinarySplitStrategy implements BisectionSplitStrategy {

    @Override
    public SplitResult split(List<QueuedPr> prs, String repository, String batchId,
                             String targetBranch, int bisectionDepth,
                             String bisectionStrategy, String riskLevel) {
        if (prs.size() < 2) {
            throw new IllegalArgumentException("Cannot split a batch with fewer than 2 PRs");
        }

        int            midpoint = prs.size() / 2;
        List<QueuedPr> leftPrs  = List.copyOf(prs.subList(0, midpoint));
        List<QueuedPr> rightPrs = List.copyOf(prs.subList(midpoint, prs.size()));

        var left = new BatchSlice(
                batchId + "-L", repository, targetBranch, leftPrs, leftPrs.size(),
                batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        var right = new BatchSlice(
                batchId + "-R", repository, targetBranch, rightPrs, rightPrs.size(),
                batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        return new SplitResult(left, right);
    }
}
