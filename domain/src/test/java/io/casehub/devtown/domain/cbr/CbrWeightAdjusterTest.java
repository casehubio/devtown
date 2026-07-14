package io.casehub.devtown.domain.cbr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CbrWeightAdjusterTest {

    private static final double RATE = 0.1;
    private static final Map<String, Double> WEIGHTS = Map.of(
            "filePaths", 1.0, "modules", 1.5, "languages", 0.5);

    @Test
    void correctPrediction_increasesContributingWeights() {
        var breakdown = Map.of("filePaths", 0.8, "modules", 0.6, "languages", 0.0);
        var adjusted = CbrWeightAdjuster.adjust(WEIGHTS, breakdown, true, RATE);

        assertThat(adjusted.get("filePaths")).isCloseTo(1.08, within(0.001));
        assertThat(adjusted.get("modules")).isCloseTo(1.56, within(0.001));
        assertThat(adjusted.get("languages")).isCloseTo(0.5, within(0.001));
    }

    @Test
    void incorrectPrediction_decreasesContributingWeights() {
        var breakdown = Map.of("filePaths", 0.8, "modules", 0.6, "languages", 0.0);
        var adjusted = CbrWeightAdjuster.adjust(WEIGHTS, breakdown, false, RATE);

        assertThat(adjusted.get("filePaths")).isCloseTo(0.92, within(0.001));
        assertThat(adjusted.get("modules")).isCloseTo(1.44, within(0.001));
        assertThat(adjusted.get("languages")).isCloseTo(0.5, within(0.001));
    }

    @Test
    void incorrectPrediction_neverGoesBelow001() {
        var lowWeights = Map.of("filePaths", 0.05);
        var breakdown = Map.of("filePaths", 0.9);
        var adjusted = CbrWeightAdjuster.adjust(lowWeights, breakdown, false, 0.5);

        assertThat(adjusted.get("filePaths")).isCloseTo(0.01, within(0.001));
    }

    @Test
    void unknownDimensionInBreakdown_ignored() {
        var breakdown = Map.of("filePaths", 0.5, "unknown", 0.3);
        var adjusted = CbrWeightAdjuster.adjust(WEIGHTS, breakdown, true, RATE);

        assertThat(adjusted).doesNotContainKey("unknown");
        assertThat(adjusted.get("filePaths")).isCloseTo(1.05, within(0.001));
    }

    @Test
    void emptyBreakdown_returnsUnchangedWeights() {
        var adjusted = CbrWeightAdjuster.adjust(WEIGHTS, Map.of(), true, RATE);
        assertThat(adjusted).isEqualTo(WEIGHTS);
    }

    @Test
    void higherLearningRate_producesLargerAdjustments() {
        var breakdown = Map.of("filePaths", 1.0);
        var slowAdj = CbrWeightAdjuster.adjust(WEIGHTS, breakdown, true, 0.05);
        var fastAdj = CbrWeightAdjuster.adjust(WEIGHTS, breakdown, true, 0.2);

        assertThat(fastAdj.get("filePaths") - 1.0)
                .isGreaterThan(slowAdj.get("filePaths") - 1.0);
    }
}
