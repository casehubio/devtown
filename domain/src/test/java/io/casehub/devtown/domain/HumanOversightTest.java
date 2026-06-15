package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HumanOversightTest {

    @Test
    void constantNonBlank() {
        assertThat(HumanOversight.ROUTING_REVIEW).isNotBlank();
    }

    @Test
    void valueMatchesSpec() {
        assertThat(HumanOversight.ROUTING_REVIEW).isEqualTo("human-oversight:routing-review");
    }

    @Test
    void prefixedCorrectly() {
        assertThat(HumanOversight.ROUTING_REVIEW).startsWith("human-oversight:");
    }

    @Test
    void noOverlapWithHumanDecision() {
        assertThat(HumanOversight.ROUTING_REVIEW)
            .doesNotStartWith("human-decision:")
            .isNotEqualTo(HumanDecision.PR_APPROVAL);
    }

    @Test
    void generalConstantNonBlank() {
        assertThat(HumanOversight.GENERAL).isNotBlank();
    }

    @Test
    void generalValueMatchesSpec() {
        assertThat(HumanOversight.GENERAL).isEqualTo("human-oversight:general");
    }

    @Test
    void generalPrefixedCorrectly() {
        assertThat(HumanOversight.GENERAL).startsWith("human-oversight:");
    }

    @Test
    void generalNoOverlapWithHumanDecision() {
        assertThat(HumanOversight.GENERAL)
            .doesNotStartWith("human-decision:")
            .isNotEqualTo(HumanDecision.PR_APPROVAL);
    }
}
