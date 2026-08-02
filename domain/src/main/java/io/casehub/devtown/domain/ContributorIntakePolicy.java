package io.casehub.devtown.domain;

import java.util.Objects;
import java.util.OptionalDouble;

public record ContributorIntakePolicy(
    double fastTrackThreshold,
    int fastTrackMinObservations,
    double standardThreshold,
    int standardMinObservations,
    String rationale
) {
    public ContributorIntakePolicy {
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (fastTrackThreshold < standardThreshold) {
            throw new IllegalArgumentException(
                "fastTrackThreshold must be >= standardThreshold");
        }
    }

    public IntakeClassification classify(OptionalDouble score, int observations) {
        if (score.isEmpty()) {
            return new IntakeClassification(
                IntakeLane.TRIAGE, Double.NaN, observations,
                "no trust score — contributor has no history");
        }
        double s = score.getAsDouble();
        if (s >= fastTrackThreshold && observations >= fastTrackMinObservations) {
            return new IntakeClassification(
                IntakeLane.FAST_TRACK, s, observations,
                String.format("score %.2f >= fast-track threshold %.2f with %d observations",
                    s, fastTrackThreshold, observations));
        }
        if (s >= standardThreshold && observations >= standardMinObservations) {
            return new IntakeClassification(
                IntakeLane.STANDARD, s, observations,
                String.format("score %.2f >= standard threshold %.2f with %d observations",
                    s, standardThreshold, observations));
        }
        String reason = observations < standardMinObservations
            ? String.format("insufficient observations (%d < %d)", observations, standardMinObservations)
            : String.format("score %.2f below standard threshold %.2f", s, standardThreshold);
        return new IntakeClassification(IntakeLane.TRIAGE, s, observations, reason);
    }
}
