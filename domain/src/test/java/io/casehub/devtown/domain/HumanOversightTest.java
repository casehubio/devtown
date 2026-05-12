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
}
