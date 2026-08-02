package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class ContributorIntakePolicyTest {

    private final ContributorIntakePolicy policy = new ContributorIntakePolicy(
        0.75, 10,
        0.50, 3,
        "test policy"
    );

    @Test
    void noScore_classifiesAsTriage() {
        var result = policy.classify(OptionalDouble.empty(), 0);
        assertEquals(IntakeLane.TRIAGE, result.lane());
        assertTrue(Double.isNaN(result.trustScore()));
        assertEquals(0, result.observationCount());
    }

    @Test
    void highScoreHighObservations_fastTrack() {
        var result = policy.classify(OptionalDouble.of(0.80), 15);
        assertEquals(IntakeLane.FAST_TRACK, result.lane());
        assertEquals(0.80, result.trustScore(), 0.001);
    }

    @Test
    void highScoreInsufficientObservations_triage() {
        var result = policy.classify(OptionalDouble.of(0.80), 2);
        assertEquals(IntakeLane.TRIAGE, result.lane());
    }

    @Test
    void mediumScoreSufficientObservations_standard() {
        var result = policy.classify(OptionalDouble.of(0.60), 5);
        assertEquals(IntakeLane.STANDARD, result.lane());
    }

    @Test
    void lowScoreHighObservations_triage() {
        var result = policy.classify(OptionalDouble.of(0.40), 20);
        assertEquals(IntakeLane.TRIAGE, result.lane());
    }

    @Test
    void exactlyAtFastTrackThreshold_inclusive() {
        var result = policy.classify(OptionalDouble.of(0.75), 10);
        assertEquals(IntakeLane.FAST_TRACK, result.lane());
    }

    @Test
    void exactlyAtStandardThreshold_inclusive() {
        var result = policy.classify(OptionalDouble.of(0.50), 3);
        assertEquals(IntakeLane.STANDARD, result.lane());
    }

    @Test
    void belowStandardThreshold_triage() {
        var result = policy.classify(OptionalDouble.of(0.49), 5);
        assertEquals(IntakeLane.TRIAGE, result.lane());
    }

    @Test
    void classificationReason_containsScoreAndThreshold() {
        var result = policy.classify(OptionalDouble.of(0.80), 15);
        assertTrue(result.classificationReason().contains("0.8"));
        assertTrue(result.classificationReason().contains("0.75"));
    }

    @Test
    void fastTrackThresholdBelowStandard_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            new ContributorIntakePolicy(0.40, 10, 0.50, 3, "invalid"));
    }

    @Test
    void nullRationale_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            new ContributorIntakePolicy(0.75, 10, 0.50, 3, null));
    }

    @Test
    void highScoreBetweenThresholds_standard() {
        var result = policy.classify(OptionalDouble.of(0.70), 15);
        assertEquals(IntakeLane.STANDARD, result.lane());
    }

    @Test
    void standardScoreInsufficientObservations_triage() {
        var result = policy.classify(OptionalDouble.of(0.60), 2);
        assertEquals(IntakeLane.TRIAGE, result.lane());
    }
}
