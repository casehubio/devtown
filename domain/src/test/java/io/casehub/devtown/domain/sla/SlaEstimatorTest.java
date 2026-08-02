package io.casehub.devtown.domain.sla;

import io.casehub.devtown.domain.cbr.CapabilityOutcome;
import io.casehub.devtown.domain.cbr.PrFeatureVector;
import io.casehub.devtown.domain.cbr.Precedent;
import io.casehub.devtown.domain.cbr.SimilarityScore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlaEstimatorTest {

    private static Precedent precedent(Duration completionTime) {
        return new Precedent(
            UUID.randomUUID(),
            new SimilarityScore(0.8, Map.of()),
            new PrFeatureVector("r", 1, "a", 10, Set.of(), Set.of(), Set.of(), false, false),
            "approved",
            Map.of("style-review", new CapabilityOutcome("COMPLETED", "approved", null)),
            completionTime
        );
    }

    @Test
    void emptyPrecedents_returnsEmpty() {
        assertThat(SlaEstimator.estimate(List.of())).isEmpty();
    }

    @Test
    void allNullDurations_returnsEmpty() {
        assertThat(SlaEstimator.estimate(List.of(
            precedent(null), precedent(null)
        ))).isEmpty();
    }

    @Test
    void singlePrecedent_medianEqualsThatDuration() {
        var result = SlaEstimator.estimate(List.of(precedent(Duration.ofMinutes(10))));
        assertThat(result).isPresent();
        assertThat(result.get().overall().median()).isEqualTo(Duration.ofMinutes(10));
        assertThat(result.get().overall().sampleCount()).isEqualTo(1);
    }

    @Test
    void oddCount_middleElementIsMedian() {
        var result = SlaEstimator.estimate(List.of(
            precedent(Duration.ofMinutes(5)),
            precedent(Duration.ofMinutes(15)),
            precedent(Duration.ofMinutes(10))
        ));
        assertThat(result).isPresent();
        assertThat(result.get().overall().median()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void evenCount_upperMiddleElementIsMedian() {
        var result = SlaEstimator.estimate(List.of(
            precedent(Duration.ofMinutes(5)),
            precedent(Duration.ofMinutes(10)),
            precedent(Duration.ofMinutes(15)),
            precedent(Duration.ofMinutes(20))
        ));
        assertThat(result).isPresent();
        assertThat(result.get().overall().median()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void negativeAndZeroDurationsFiltered() {
        var result = SlaEstimator.estimate(List.of(
            precedent(Duration.ofMinutes(-5)),
            precedent(Duration.ZERO),
            precedent(Duration.ofMinutes(12))
        ));
        assertThat(result).isPresent();
        assertThat(result.get().overall().median()).isEqualTo(Duration.ofMinutes(12));
        assertThat(result.get().overall().sampleCount()).isEqualTo(1);
    }

    @Test
    void minAndMaxCorrect() {
        var result = SlaEstimator.estimate(List.of(
            precedent(Duration.ofMinutes(3)),
            precedent(Duration.ofMinutes(25)),
            precedent(Duration.ofMinutes(12))
        ));
        assertThat(result).isPresent();
        assertThat(result.get().overall().min()).isEqualTo(Duration.ofMinutes(3));
        assertThat(result.get().overall().max()).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void nullDurationsMixedWithValid_onlyValidCounted() {
        var result = SlaEstimator.estimate(List.of(
            precedent(null),
            precedent(Duration.ofMinutes(7)),
            precedent(Duration.ofMinutes(14)),
            precedent(null)
        ));
        assertThat(result).isPresent();
        assertThat(result.get().overall().sampleCount()).isEqualTo(2);
        assertThat(result.get().overall().median()).isEqualTo(Duration.ofMinutes(14));
    }

    @Test
    void perCapabilityBreakdown_computedFromOutcomeDurations() {
        var p1 = new Precedent(UUID.randomUUID(),
                               new SimilarityScore(0.8, Map.of()),
                               new PrFeatureVector("r", 1, "a", 10, Set.of(), Set.of(), Set.of(), false, false),
                               "approved",
                               Map.of("code-analysis", new CapabilityOutcome("COMPLETED", "approved", Duration.ofMinutes(3)),
                                      "security-review", new CapabilityOutcome("COMPLETED", "approved", Duration.ofMinutes(8))),
                               Duration.ofMinutes(10));
        var p2 = new Precedent(UUID.randomUUID(),
                               new SimilarityScore(0.7, Map.of()),
                               new PrFeatureVector("r", 2, "b", 20, Set.of(), Set.of(), Set.of(), false, false),
                               "approved",
                               Map.of("code-analysis", new CapabilityOutcome("COMPLETED", "approved", Duration.ofMinutes(5)),
                                      "security-review", new CapabilityOutcome("COMPLETED", "approved", Duration.ofMinutes(12))),
                               Duration.ofMinutes(15));
        var p3 = new Precedent(UUID.randomUUID(),
                               new SimilarityScore(0.9, Map.of()),
                               new PrFeatureVector("r", 3, "c", 5, Set.of(), Set.of(), Set.of(), false, false),
                               "approved",
                               Map.of("code-analysis", new CapabilityOutcome("COMPLETED", "approved", Duration.ofMinutes(4))),
                               Duration.ofMinutes(8));

        var result = SlaEstimator.estimate(List.of(p1, p2, p3));
        assertThat(result).isPresent();

        var breakdown = result.get().capabilityBreakdown();
        assertThat(breakdown).containsKeys("code-analysis", "security-review");

        var codeAnalysis = breakdown.get("code-analysis");
        assertThat(codeAnalysis.sampleCount()).isEqualTo(3);
        assertThat(codeAnalysis.median()).isEqualTo(Duration.ofMinutes(4));
        assertThat(codeAnalysis.min()).isEqualTo(Duration.ofMinutes(3));
        assertThat(codeAnalysis.max()).isEqualTo(Duration.ofMinutes(5));

        var securityReview = breakdown.get("security-review");
        assertThat(securityReview.sampleCount()).isEqualTo(2);
        assertThat(securityReview.median()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void perCapabilityBreakdown_nullDurationsExcluded() {
        var p1 = new Precedent(UUID.randomUUID(),
                               new SimilarityScore(0.8, Map.of()),
                               new PrFeatureVector("r", 1, "a", 10, Set.of(), Set.of(), Set.of(), false, false),
                               "approved",
                               Map.of("code-analysis", new CapabilityOutcome("COMPLETED", "approved", null)),
                               Duration.ofMinutes(10));

        var result = SlaEstimator.estimate(List.of(p1));
        assertThat(result).isPresent();
        assertThat(result.get().capabilityBreakdown()).isEmpty();
    }

    @Test
    void perCapabilityBreakdown_zeroDurationIncluded() {
        var p1 = new Precedent(UUID.randomUUID(),
                               new SimilarityScore(0.8, Map.of()),
                               new PrFeatureVector("r", 1, "a", 10, Set.of(), Set.of(), Set.of(), false, false),
                               "approved",
                               Map.of("style-review", new CapabilityOutcome("COMPLETED", "approved", Duration.ZERO)),
                               Duration.ofMinutes(10));

        var result = SlaEstimator.estimate(List.of(p1));
        assertThat(result).isPresent();
        assertThat(result.get().capabilityBreakdown()).containsKey("style-review");
        assertThat(result.get().capabilityBreakdown().get("style-review").median()).isEqualTo(Duration.ZERO);
    }

    @Test
    void perCapabilityBreakdown_emptyOutcomes_emptyBreakdown() {
        var result = SlaEstimator.estimate(List.of(precedent(Duration.ofMinutes(10))));
        assertThat(result).isPresent();
        assertThat(result.get().capabilityBreakdown()).isEmpty();
    }
}
