package io.casehub.devtown.app.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaseTrackingStatusTest {

    @Test
    void superseded_isTerminal() {
        assertThat(CaseTrackingStatus.SUPERSEDED.isTerminal()).isTrue();
    }

    @Test
    void fromCaseStatus_doesNotMapToSuperseded() {
        // SUPERSEDED is a devtown-internal state set explicitly, not mapped from engine
        assertThat(CaseTrackingStatus.fromCaseStatus("SUPERSEDED")).isNotEqualTo(CaseTrackingStatus.SUPERSEDED);
    }

    @Test
    void allTerminalStates_areExactly_completed_faulted_cancelled_superseded() {
        for (CaseTrackingStatus status : CaseTrackingStatus.values()) {
            boolean expected = status == CaseTrackingStatus.COMPLETED
                || status == CaseTrackingStatus.FAULTED
                || status == CaseTrackingStatus.CANCELLED
                || status == CaseTrackingStatus.SUPERSEDED;
            assertThat(status.isTerminal())
                .as("isTerminal() for %s", status)
                .isEqualTo(expected);
        }
    }
}
