package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HumanDecisionTest {

    @Test
    void constantNonBlank() {
        assertThat(HumanDecision.PR_APPROVAL).isNotBlank();
    }

    @Test
    void valueMatchesSpec() {
        assertThat(HumanDecision.PR_APPROVAL).isEqualTo("human-decision:pr-approval");
    }

    @Test
    void prefixedCorrectly() {
        assertThat(HumanDecision.PR_APPROVAL).startsWith("human-decision:");
    }

    @Test
    void noOverlapWithOtherTypes() {
        assertThat(HumanDecision.PR_APPROVAL)
            .doesNotStartWith("human-oversight:")
            .isNotEqualTo(AgentQualification.CI_RUNNER)
            .isNotEqualTo(AgentQualification.MERGE_EXECUTOR);
    }
}
