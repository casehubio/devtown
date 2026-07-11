package io.casehub.devtown.review;

import io.casehub.devtown.domain.cbr.PrFeatureVector;
import io.casehub.devtown.domain.cbr.SimilarityScore;

import java.util.Map;
import java.util.UUID;

public record Precedent(
    UUID caseId,
    SimilarityScore similarity,
    PrFeatureVector vector,
    String outcome,
    Map<String, String> capabilityOutcomes
) {}
