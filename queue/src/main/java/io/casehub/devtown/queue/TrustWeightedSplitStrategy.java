package io.casehub.devtown.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class TrustWeightedSplitStrategy implements BisectionSplitStrategy {

    @Override
    public SplitResult split(List<Map<String, Object>> prs, String batchId,
                              String targetBranch, int bisectionDepth,
                              String bisectionStrategy, String riskLevel) {
        if (prs.size() < 2) {
            throw new IllegalArgumentException("Cannot split a batch with fewer than 2 PRs");
        }

        List<Map<String, Object>> sorted = new ArrayList<>(prs);
        sorted.sort(Comparator.comparingDouble(m -> (double) m.get("trustScore")));

        int midpoint = sorted.size() / 2;
        List<Map<String, Object>> leftPrs = List.copyOf(sorted.subList(0, midpoint));
        List<Map<String, Object>> rightPrs = List.copyOf(sorted.subList(midpoint, sorted.size()));

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
