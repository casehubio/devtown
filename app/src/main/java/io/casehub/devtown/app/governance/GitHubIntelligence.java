package io.casehub.devtown.app.governance;

import java.time.Instant;

public record GitHubIntelligence(
    int mergedCount,
    int closedCount,
    double mergeRatio,
    String repoConfidenceTier,
    String cacheMaturity,
    Instant lastRefreshed,
    boolean bootstrapped,
    String bootstrapSummary
) {}
