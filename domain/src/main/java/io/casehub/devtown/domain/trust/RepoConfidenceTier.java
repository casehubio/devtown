package io.casehub.devtown.domain.trust;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public enum RepoConfidenceTier {
    HIGH(0.8),
    MEDIUM(0.5),
    LOW(0.3);

    private final double confidenceMultiplier;

    RepoConfidenceTier(double confidenceMultiplier) {
        this.confidenceMultiplier = confidenceMultiplier;
    }

    public double confidenceMultiplier() {
        return confidenceMultiplier;
    }

    public static RepoConfidenceTier classify(int contributorCount, int pullRequestCount,
                                               Instant createdAt, Instant now) {
        long ageInDays = ChronoUnit.DAYS.between(createdAt, now);
        if (contributorCount >= 5 && pullRequestCount >= 50 && ageInDays >= 365) {
            return MEDIUM;
        }
        return LOW;
    }
}
