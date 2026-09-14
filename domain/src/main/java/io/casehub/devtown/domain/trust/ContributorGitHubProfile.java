package io.casehub.devtown.domain.trust;

import java.time.Instant;

public record ContributorGitHubProfile(
    String login,
    long contributorNumericId,
    String repo,
    String actorId,
    int mergedCount,
    int closedCount,
    int observationCount,
    Instant lastRefreshAt,
    CacheMaturity maturity
) {
    public static String actorIdFromGitHubNumericId(long numericId) {
        return "github-id:" + numericId;
    }
}
