package io.casehub.devtown.domain.cbr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrecedentActivationPolicyTest {

    private static final SimilarityScore SCORE = new SimilarityScore(0.8, Map.of());
    private static final PrFeatureVector VECTOR = new PrFeatureVector(
        "repo", 1, "dev", 100, Set.of(), Set.of(), Set.of(), false, false);

    private static final CapabilityOutcome FINDINGS = new CapabilityOutcome("COMPLETED", "FINDINGS_PRESENT");
    private static final CapabilityOutcome APPROVED = new CapabilityOutcome("COMPLETED", "approved");
    private static final CapabilityOutcome FAILED = new CapabilityOutcome("FAILED", null);

    private Precedent precedent(Map<String, CapabilityOutcome> outcomes) {
        return new Precedent(UUID.randomUUID(), SCORE, VECTOR, "flagged", outcomes);
    }

    @Test
    void emptyPrecedentsReturnsEmptySet() {
        assertThat(PrecedentActivationPolicy.evaluate(List.of(), 2, 0.4)).isEmpty();
    }

    @Test
    void belowMinFindingsNotActivated() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4)).isEmpty();
    }

    @Test
    void belowMinFractionNotActivated() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4)).isEmpty();
    }

    @Test
    void meetsBothThresholdsActivated() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("security-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4))
            .containsExactly("security-review");
    }

    @Test
    void multipleCapabilitiesEvaluatedIndependently() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS, "architecture-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS, "architecture-review", APPROVED)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", APPROVED)),
            precedent(Map.of("architecture-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4))
            .containsExactly("security-review");
    }

    @Test
    void exactBoundaryMinFindingsActivated() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4))
            .containsExactly("security-review");
    }

    @Test
    void failedOutcomeNotCountedAsFindings() {
        var precedents = List.of(
            precedent(Map.of("security-review", FAILED)),
            precedent(Map.of("security-review", FAILED)),
            precedent(Map.of("security-review", FAILED)),
            precedent(Map.of("security-review", FAILED)),
            precedent(Map.of("security-review", FAILED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4)).isEmpty();
    }

    @Test
    void capabilityAbsentFromPrecedentNotCounted() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS)),
            precedent(Map.of("style-review", APPROVED)),
            precedent(Map.of("style-review", APPROVED)),
            precedent(Map.of("style-review", APPROVED))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4))
            .containsExactly("security-review");
    }

    @Test
    void multipleCapabilitiesBothActivated() {
        var precedents = List.of(
            precedent(Map.of("security-review", FINDINGS, "architecture-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS, "architecture-review", FINDINGS)),
            precedent(Map.of("security-review", FINDINGS, "architecture-review", FINDINGS))
        );
        assertThat(PrecedentActivationPolicy.evaluate(precedents, 2, 0.4))
            .containsExactlyInAnyOrder("security-review", "architecture-review");
    }
}
