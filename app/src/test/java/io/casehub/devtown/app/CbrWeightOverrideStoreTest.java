package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CbrWeightOverrideStoreTest {

    private static final Map<String, Double> WEIGHTS = Map.of(
            "filePaths", 1.0, "modules", 1.5, "languages", 0.5);
    private static final Map<String, Double> BREAKDOWN = Map.of(
            "filePaths", 0.8, "modules", 0.6);

    @Test
    void belowMinSamples_noAdjustment() {
        var store = new CbrWeightOverrideStore(5, 0.1);
        store.recordOutcome(WEIGHTS, BREAKDOWN, true);
        store.recordOutcome(WEIGHTS, BREAKDOWN, true);

        assertThat(store.sampleCount()).isEqualTo(2);
        assertThat(store.currentOverrides()).isEmpty();
        assertThat(store.resolveWeight("filePaths", 1.0)).isEqualTo(1.0);
    }

    @Test
    void atMinSamples_adjustmentApplied() {
        var store = new CbrWeightOverrideStore(3, 0.1);
        store.recordOutcome(WEIGHTS, BREAKDOWN, true);
        store.recordOutcome(WEIGHTS, BREAKDOWN, true);
        store.recordOutcome(WEIGHTS, BREAKDOWN, true);

        assertThat(store.sampleCount()).isEqualTo(3);
        assertThat(store.currentOverrides()).isNotEmpty();
        assertThat(store.resolveWeight("filePaths", 1.0)).isCloseTo(1.08, within(0.001));
    }

    @Test
    void incorrectOutcome_decreasesWeights() {
        var store = new CbrWeightOverrideStore(1, 0.1);
        store.recordOutcome(WEIGHTS, BREAKDOWN, false);

        assertThat(store.resolveWeight("filePaths", 1.0)).isCloseTo(0.92, within(0.001));
        assertThat(store.resolveWeight("modules", 1.5)).isCloseTo(1.44, within(0.001));
    }

    @Test
    void resolveWeight_fallsBackToDefault() {
        var store = new CbrWeightOverrideStore();
        assertThat(store.resolveWeight("filePaths", 1.0)).isEqualTo(1.0);
    }
}
