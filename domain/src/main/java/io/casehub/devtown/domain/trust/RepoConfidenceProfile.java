package io.casehub.devtown.domain.trust;

import java.time.Instant;

public record RepoConfidenceProfile(
    String repo,
    int starCount,
    int contributorCount,
    int pullRequestCount,
    Instant createdAt,
    Instant lastPushedAt,
    RepoConfidenceTier tier,
    boolean adminOverride,
    Instant lastRefreshAt
) {}
