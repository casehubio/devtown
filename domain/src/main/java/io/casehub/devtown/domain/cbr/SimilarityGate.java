package io.casehub.devtown.domain.cbr;

import java.util.Set;

public record SimilarityGate(
    int minModuleOverlap,
    double minChangeSizeRatio,
    boolean requireSameRepo
) {

    public static final SimilarityGate DISABLED = new SimilarityGate(0, 0.0, true);

    public boolean passes(PrFeatureVector query, PrFeatureVector candidate) {
        if (requireSameRepo && !query.repo().equals(candidate.repo())) {
            return false;
        }
        if (minModuleOverlap > 0) {
            long overlap = query.modules().stream().filter(candidate.modules()::contains).count();
            if (overlap < minModuleOverlap) return false;
        }
        if (minChangeSizeRatio > 0.0) {
            double ratio = WeightedJaccardSimilarity.changeSizeRatio(
                query.linesChanged(), candidate.linesChanged());
            if (ratio < minChangeSizeRatio) return false;
        }
        return true;
    }
}
