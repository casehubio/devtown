package io.casehub.devtown.domain.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BooleanPreferenceTest {

    @Test
    void of_true() {
        assertThat(BooleanPreference.of(true).value()).isTrue();
    }

    @Test
    void of_false() {
        assertThat(BooleanPreference.of(false).value()).isFalse();
    }

    @Test
    void parse_true() {
        assertThat(BooleanPreference.parse("true").value()).isTrue();
        assertThat(BooleanPreference.parse("TRUE").value()).isTrue();
        assertThat(BooleanPreference.parse(" True ").value()).isTrue();
    }

    @Test
    void parse_false() {
        assertThat(BooleanPreference.parse("false").value()).isFalse();
        assertThat(BooleanPreference.parse("FALSE").value()).isFalse();
        assertThat(BooleanPreference.parse(" False ").value()).isFalse();
    }

    @Test
    void parse_rejectsTypo() {
        assertThatThrownBy(() -> BooleanPreference.parse("ture"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ture");
    }

    @Test
    void parse_rejectsNull() {
        assertThatThrownBy(() -> BooleanPreference.parse(null))
            .isInstanceOf(NullPointerException.class);
    }
}
