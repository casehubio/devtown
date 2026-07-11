package io.casehub.devtown.domain.cbr;

public interface SimilarityMetric {
    SimilarityScore compute(PrFeatureVector a, PrFeatureVector b);
}
