package io.casehub.devtown.domain.trust;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapIntegrationLogicTest {

    @Test
    void fullPipeline_fetchHistory_computeScores_verifyAlphaBeta() {
        var snapshot = new ContributorHistorySnapshot(
                "alice", 123L, "org/repo", 15, 5,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));

        var meta = new RepoMetadataSnapshot("org/repo", 500, 20, 200,
                                            Instant.parse("2020-01-01T00:00:00Z"),
                                            Instant.parse("2026-09-01T00:00:00Z"));

        var tier = RepoConfidenceTier.classify(meta.contributorCount(),
                                               meta.pullRequestCount(), meta.createdAt(), Instant.now());
        assertEquals(RepoConfidenceTier.MEDIUM, tier);

        String actorId = ContributorGitHubProfile.actorIdFromGitHubNumericId(
                snapshot.contributorNumericId());
        assertEquals("github-id:123", actorId);

        int observationCount = snapshot.mergedCount() + snapshot.closedCount();
        var profile = new ContributorGitHubProfile(
                snapshot.login(), snapshot.contributorNumericId(), snapshot.repo(),
                actorId, snapshot.mergedCount(), snapshot.closedCount(),
                observationCount, Instant.now(),
                CacheMaturity.forObservationCount(observationCount));

        assertEquals(CacheMaturity.WARM, profile.maturity());

        var result = BootstrapScoreComputer.compute(profile, tier);
        assertFalse(result.isEmpty());
        assertEquals(7.5, result.alpha(), 0.001);
        assertEquals(2.5, result.beta(), 0.001);
        assertEquals(0.75, result.mergeRate(), 0.001);
        assertEquals(0.75, result.trustScore(), 0.001);
        assertEquals("pr-contribution", result.capabilityTag());
        assertEquals("merge-rate", result.mergeRateDimension());
    }

    @Test
    void fullPipeline_lowTierRepo_dampenedSignificantly() {
        var snapshot = new ContributorHistorySnapshot(
            "bob", 456L, "personal/toy", 10, 0,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));

        var meta = new RepoMetadataSnapshot("personal/toy", 2, 1, 10,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));

        var tier = RepoConfidenceTier.classify(meta.contributorCount(),
            meta.pullRequestCount(), meta.createdAt(), Instant.now());
        assertEquals(RepoConfidenceTier.LOW, tier);

        String actorId = ContributorGitHubProfile.actorIdFromGitHubNumericId(456L);
        int obs = snapshot.mergedCount() + snapshot.closedCount();
        var profile = new ContributorGitHubProfile(
            snapshot.login(), snapshot.contributorNumericId(), snapshot.repo(),
            actorId, snapshot.mergedCount(), snapshot.closedCount(),
            obs, Instant.now(), CacheMaturity.forObservationCount(obs));

        var result = BootstrapScoreComputer.compute(profile, tier);
        assertEquals(3.0, result.alpha(), 0.001);  // 10 * 0.3
        assertEquals(0.0, result.beta(), 0.001);   // 0 * 0.3
    }

    @Test
    void emptyContributor_noImport() {
        var snapshot = new ContributorHistorySnapshot(
            "newbie", 789L, "org/repo", 0, 0, null, null);

        String actorId = ContributorGitHubProfile.actorIdFromGitHubNumericId(789L);
        var profile = new ContributorGitHubProfile(
            "newbie", 789L, "org/repo", actorId, 0, 0, 0,
            Instant.now(), CacheMaturity.COLD);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.MEDIUM);
        assertTrue(result.isEmpty());
    }
}
