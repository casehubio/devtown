package io.casehub.devtown.domain.cbr;

import java.util.Map;

public record SimilarityScore(
    double score,
    Map<String, Double> breakdown
) implements Comparable<SimilarityScore> {

    public SimilarityScore {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0,1], got: " + score);
        }
        breakdown = Map.copyOf(breakdown);
    }

    @Override
    public int compareTo(SimilarityScore other) {
        return Double.compare(this.score, other.score);
    }
}
