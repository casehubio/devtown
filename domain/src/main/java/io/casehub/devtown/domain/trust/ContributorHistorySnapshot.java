package io.casehub.devtown.domain.trust;

import java.time.Instant;

public record ContributorHistorySnapshot(
    String login,
    long contributorNumericId,
    String repo,
    int mergedCount,
    int closedCount,
    Instant oldestPrAt,
    Instant newestPrAt
) {}
