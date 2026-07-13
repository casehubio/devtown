package io.casehub.devtown.domain.cbr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityOutcomeTest {

    @ParameterizedTest
    @CsvSource({
        "COMPLETED,,true",
        "COMPLETED,FINDINGS_PRESENT,true",
        "COMPLETED,flagged,true",
        "COMPLETED,needs-work,true",
        "COMPLETED,approved,false",
        "COMPLETED,passed,false",
        "COMPLETED,APPROVED,false",
        "COMPLETED,Passed,false",
        "FAILED,,false",
        "FAILED,approved,false",
        "FAILED,FINDINGS_PRESENT,false",
        "DECLINED,,false",
        "DECLINED,outside-scope,false"
    })
    void hadFindings(String outcome, String detail, boolean expected) {
        assertThat(new CapabilityOutcome(outcome, detail).hadFindings())
            .as("CapabilityOutcome(%s, %s)", outcome, detail)
            .isEqualTo(expected);
    }

    @Test
    void nullOutcomeIsNotFindings() {
        assertThat(new CapabilityOutcome(null, null).hadFindings()).isFalse();
    }
}
