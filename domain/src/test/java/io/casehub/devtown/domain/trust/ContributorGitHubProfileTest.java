package io.casehub.devtown.domain.trust;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ContributorGitHubProfileTest {

    @Test
    void actorIdFromGitHubNumericId_matchesAttestationPipelineFormat() {
        assertEquals("github-id:42", ContributorGitHubProfile.actorIdFromGitHubNumericId(42));
        assertEquals("github-id:123456", ContributorGitHubProfile.actorIdFromGitHubNumericId(123456));
        assertEquals("github-id:0", ContributorGitHubProfile.actorIdFromGitHubNumericId(0));
    }

    @Test
    void recordConstruction_andAccessors() {
        var now = Instant.now();
        var profile = new ContributorGitHubProfile(
            "alice", 123L, "org/repo", "github-id:123",
            10, 3, 13, now, CacheMaturity.WARM);

        assertEquals("alice", profile.login());
        assertEquals(123L, profile.contributorNumericId());
        assertEquals("org/repo", profile.repo());
        assertEquals("github-id:123", profile.actorId());
        assertEquals(10, profile.mergedCount());
        assertEquals(3, profile.closedCount());
        assertEquals(13, profile.observationCount());
        assertEquals(now, profile.lastRefreshAt());
        assertEquals(CacheMaturity.WARM, profile.maturity());
    }

    @Test
    void repoConfidenceProfile_construction() {
        var now = Instant.now();
        var profile = new RepoConfidenceProfile(
            "org/repo", 500, 20, 200,
            Instant.parse("2020-01-01T00:00:00Z"), now,
            RepoConfidenceTier.MEDIUM, false, now);

        assertEquals("org/repo", profile.repo());
        assertEquals(500, profile.starCount());
        assertEquals(RepoConfidenceTier.MEDIUM, profile.tier());
        assertFalse(profile.adminOverride());
    }
}
