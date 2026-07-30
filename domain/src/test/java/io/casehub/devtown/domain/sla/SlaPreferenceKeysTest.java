package io.casehub.devtown.domain.sla;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPreferenceKeysTest {

    @Test
    void overrideEnabled_defaultIsFalse() {
        assertThat(SlaPreferenceKeys.OVERRIDE_ENABLED.defaultValue().value()).isFalse();
    }

    @Test
    void overrideMinPrecedents_defaultIsFive() {
        assertThat(SlaPreferenceKeys.OVERRIDE_MIN_PRECEDENTS.defaultValue().value()).isEqualTo(5);
    }

    @Test
    void completionHours_defaultIs24() {
        assertThat(SlaPreferenceKeys.COMPLETION_HOURS.defaultValue().value()).isEqualTo(24);
    }
}
