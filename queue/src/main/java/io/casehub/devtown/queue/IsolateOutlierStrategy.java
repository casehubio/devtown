package io.casehub.devtown.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class IsolateOutlierStrategy implements BisectionSplitStrategy {

    private final TrustWeightedSplitStrategy fallback = new TrustWeightedSplitStrategy();

    @Override
    public SplitResult split(List<Map<String, Object>> prs, String batchId,
                              String targetBranch, int bisectionDepth,
                              String bisectionStrategy, String riskLevel) {
        if (prs.size() < 2) {
            throw new IllegalArgumentException("Cannot split a batch with fewer than 2 PRs");
        }

        double[] scores = prs.stream()
            .mapToDouble(m -> (double) m.get("trustScore"))
            .toArray();

        double mean = 0.0;
        for (double s : scores) mean += s;
        mean /= scores.length;

        double variance = 0.0;
        for (double s : scores) variance += (s - mean) * (s - mean);
        variance /= scores.length;
        double stddev = Math.sqrt(variance);

        double threshold = mean - 2.0 * stddev;

        // Find the PR with the lowest trust that is below threshold
        int outlierIndex = -1;
        double lowestTrust = Double.MAX_VALUE;
        for (int i = 0; i < prs.size(); i++) {
            double trust = (double) prs.get(i).get("trustScore");
            if (trust < threshold && trust < lowestTrust) {
                outlierIndex = i;
                lowestTrust = trust;
            }
        }

        if (outlierIndex < 0) {
            return fallback.split(prs, batchId, targetBranch, bisectionDepth,
                                  bisectionStrategy, riskLevel);
        }

        // Isolate the outlier as a solo left batch
        Map<String, Object> outlier = prs.get(outlierIndex);
        List<Map<String, Object>> rest = new ArrayList<>(prs);
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
