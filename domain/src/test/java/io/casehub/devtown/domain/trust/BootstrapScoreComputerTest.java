package io.casehub.devtown.domain.trust;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapScoreComputerTest {

    @Test
    void compute_mergedOnly_highTier() {
        var profile = new ContributorGitHubProfile(
            "alice", 123L, "org/repo", "github-id:123",
            10, 0, 10, Instant.now(), CacheMaturity.WARM);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.HIGH);

        assertEquals(8.0, result.alpha(), 0.001);
        assertEquals(0.0, result.beta(), 0.001);
        assertEquals(1.0, result.mergeRate(), 0.001);
        assertEquals(10, result.observationCount());
        assertFalse(result.isEmpty());
    }

    @Test
    void compute_mixedOutcomes_mediumTier() {
        var profile = new ContributorGitHubProfile(
            "bob", 456L, "org/repo", "github-id:456",
            7, 3, 10, Instant.now(), CacheMaturity.WARM);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.MEDIUM);

        assertEquals(3.5, result.alpha(), 0.001);
        assertEquals(1.5, result.beta(), 0.001);
        assertEquals(0.7, result.mergeRate(), 0.001);
    }

    @Test
    void compute_zeroObservations_uninformativePrior() {
        var profile = new ContributorGitHubProfile(
            "new", 789L, "org/repo", "github-id:789",
            0, 0, 0, Instant.now(), CacheMaturity.COLD);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.LOW);

        assertEquals(0.0, result.alpha(), 0.001);
        assertEquals(0.0, result.beta(), 0.001);
        assertEquals(0.5, result.mergeRate(), 0.001);
        assertEquals(0, result.observationCount());
        assertTrue(result.isEmpty());
    }

    @Test
    void compute_lowTier_dampensSignificantly() {
        var profile = new ContributorGitHubProfile(
            "eve", 101L, "personal/repo", "github-id:101",
            20, 5, 25, Instant.now(), CacheMaturity.HOT);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.LOW);

        assertEquals(6.0, result.alpha(), 0.001);
        assertEquals(1.5, result.beta(), 0.001);
    }

    @Test
    void trustScore_fromAlphaBeta() {
        var profile = new ContributorGitHubProfile(
            "alice", 123L, "org/repo", "github-id:123",
            8, 2, 10, Instant.now(), CacheMaturity.WARM);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.HIGH);

        assertEquals(0.8, result.trustScore(), 0.001);
    }

    @Test
    void trustScore_zeroObservations_returnsHalf() {
        var profile = new ContributorGitHubProfile(
            "new", 789L, "org/repo", "github-id:789",
            0, 0, 0, Instant.now(), CacheMaturity.COLD);

        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.LOW);

        assertEquals(0.5, result.trustScore(), 0.001);
    }

    @Test
    void actorId_deterministicFromNumericId() {
        assertEquals("github-id:42", ContributorGitHubProfile.actorIdFromGitHubNumericId(42));
        assertEquals("github-id:123456", ContributorGitHubProfile.actorIdFromGitHubNumericId(123456));
    }

    @Test
    void capabilityTag_isPrContribution() {
        var profile = new ContributorGitHubProfile(
            "alice", 123L, "org/repo", "github-id:123",
            5, 1, 6, Instant.now(), CacheMaturity.WARM);
        var result = BootstrapScoreComputer.compute(profile, RepoConfidenceTier.MEDIUM);

        assertEquals("pr-contribution", result.capabilityTag());
        assertEquals("merge-rate", result.mergeRateDimension());
    }
}
