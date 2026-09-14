package io.casehub.devtown.domain.trust;

import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.ContributorTrustDimension;

public final class BootstrapScoreComputer {

    private BootstrapScoreComputer() {}

    public static BootstrapScoreResult compute(ContributorGitHubProfile profile,
                                                RepoConfidenceTier tier) {
        double multiplier = tier.confidenceMultiplier();
        double alpha = profile.mergedCount() * multiplier;
        double beta = profile.closedCount() * multiplier;
        double mergeRate = profile.observationCount() > 0
            ? (double) profile.mergedCount() / profile.observationCount()
            : 0.5;

        return new BootstrapScoreResult(
            alpha, beta, mergeRate, profile.observationCount(),
            profile.observationCount() == 0);
    }

    public record BootstrapScoreResult(
        double alpha,
        double beta,
        double mergeRate,
        int observationCount,
        boolean isEmpty
    ) {
        public String capabilityTag() {
            return ContributorTrustCapability.PR_CONTRIBUTION;
        }

        public String mergeRateDimension() {
            return ContributorTrustDimension.MERGE_RATE;
        }

        public double trustScore() {
            if (alpha + beta == 0) return 0.5;
            return alpha / (alpha + beta);
        }
    }
}
