package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentSeverityTest {

    @ParameterizedTest
    @CsvSource({
        "LOW, 0.3",
        "MEDIUM, 0.5",
        "HIGH, 0.7",
        "CRITICAL, 0.9"
    })
    void confidenceMapping(IncidentSeverity severity, double expected) {
        assertThat(severity.confidence()).isEqualTo(expected);
    }

    @Test
    void allValuesHaveConfidence() {
        for (IncidentSeverity s : IncidentSeverity.values()) {
            assertThat(s.confidence()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void exactlyFourValues() {
        assertThat(IncidentSeverity.values()).hasSize(4);
    }
}
