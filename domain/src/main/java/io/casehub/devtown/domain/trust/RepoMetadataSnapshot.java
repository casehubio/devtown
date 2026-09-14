package io.casehub.devtown.domain.trust;

import java.time.Instant;

public record RepoMetadataSnapshot(
    String repo,
    int starCount,
    int contributorCount,
    int pullRequestCount,
    Instant createdAt,
    Instant lastPushedAt
) {}
