package io.casehub.devtown.domain.trust;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class RepoConfidenceTierTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    void multipliers() {
        assertEquals(0.8, RepoConfidenceTier.HIGH.confidenceMultiplier());
        assertEquals(0.5, RepoConfidenceTier.MEDIUM.confidenceMultiplier());
        assertEquals(0.3, RepoConfidenceTier.LOW.confidenceMultiplier());
    }

    @Test
    void classify_medium_meetsAllThresholds() {
        Instant twoYearsAgo = NOW.minus(730, ChronoUnit.DAYS);
        assertEquals(RepoConfidenceTier.MEDIUM,
            RepoConfidenceTier.classify(10, 100, twoYearsAgo, NOW));
    }

    @Test
    void classify_low_tooFewContributors() {
        Instant twoYearsAgo = NOW.minus(730, ChronoUnit.DAYS);
        assertEquals(RepoConfidenceTier.LOW,
            RepoConfidenceTier.classify(3, 100, twoYearsAgo, NOW));
    }

    @Test
    void classify_low_tooYoung() {
        Instant sixMonthsAgo = NOW.minus(180, ChronoUnit.DAYS);
        assertEquals(RepoConfidenceTier.LOW,
            RepoConfidenceTier.classify(10, 100, sixMonthsAgo, NOW));
    }

    @Test
    void classify_low_tooFewPrs() {
        Instant twoYearsAgo = NOW.minus(730, ChronoUnit.DAYS);
        assertEquals(RepoConfidenceTier.LOW,
            RepoConfidenceTier.classify(10, 30, twoYearsAgo, NOW));
    }

    @Test
    void classify_boundary_exactThresholds() {
        Instant exactlyOneYearAgo = NOW.minus(365, ChronoUnit.DAYS);
        assertEquals(RepoConfidenceTier.MEDIUM,
            RepoConfidenceTier.classify(5, 50, exactlyOneYearAgo, NOW));
    }
}
