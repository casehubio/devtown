package io.casehub.devtown.queue;

import java.util.ArrayList;
import java.util.List;

public class IsolateOutlierStrategy implements BisectionSplitStrategy {

    private final TrustWeightedSplitStrategy fallback = new TrustWeightedSplitStrategy();

    @Override
    public SplitResult split(List<QueuedPr> prs, String batchId,
                             String targetBranch, int bisectionDepth,
                             String bisectionStrategy, String riskLevel) {
        if (prs.size() < 2) {
            throw new IllegalArgumentException("Cannot split a batch with fewer than 2 PRs");
        }

        double mean = prs.stream().mapToDouble(QueuedPr::trustScore).average().orElse(1.0);
        double variance = prs.stream()
                             .mapToDouble(p -> {
                                 double d = p.trustScore() - mean;
                                 return d * d;
                             })
                             .average().orElse(0.0);
        double stddev    = Math.sqrt(variance);
        double threshold = mean - 2.0 * stddev;

        int    outlierIndex = -1;
        double lowestTrust  = Double.MAX_VALUE;
        for (int i = 0; i < prs.size(); i++) {
            double trust = prs.get(i).trustScore();
            if (trust < threshold && trust < lowestTrust) {
                outlierIndex = i;
                lowestTrust  = trust;
            }
        }

        if (outlierIndex < 0) {
            return fallback.split(prs, batchId, targetBranch, bisectionDepth,
                                  bisectionStrategy, riskLevel);
        }

        QueuedPr       outlier = prs.get(outlierIndex);
        List<QueuedPr> rest    = new ArrayList<>(prs);
        rest.remove(outlierIndex);

        var left = new BatchSlice(
                batchId + "-L", targetBranch, List.of(outlier), 1,
                batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        var right = new BatchSlice(
                batchId + "-R", targetBranch, List.copyOf(rest), rest.size(),
                batchId, bisectionDepth, bisectionStrategy, riskLevel
        );
        return new SplitResult(left, right);
    }
}
