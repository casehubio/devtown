package io.casehub.devtown.domain.spi;

import io.casehub.devtown.domain.RoutingPolicy;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityRegistrySpiTest {

    // Minimal implementation — only capabilities() and policy() provided.
    // isKnown() must work via the SPI default method without being overridden.
    private final CapabilityRegistry minimal = new CapabilityRegistry() {
        @Override
        public Set<String> capabilities() {
            return Set.of("code-analysis", "security-review");
        }

        @Override
        public Optional<RoutingPolicy> policy(String capability) {
            return Optional.empty();
        }
    };

    @Test
    void isKnownDefaultReturnsTrueForKnownCapability() {
        assertThat(minimal.isKnown("code-analysis")).isTrue();
    }

    @Test
    void isKnownDefaultReturnsFalseForUnknownCapability() {
        assertThat(minimal.isKnown("unknown")).isFalse();
    }

    @Test
    void isKnownDefaultReturnsFalseForEmptyString() {
        assertThat(minimal.isKnown("")).isFalse();
    }

    @Test
    void isKnownDefaultReturnsFalseWhenCapabilitiesIsEmpty() {
        CapabilityRegistry empty = new CapabilityRegistry() {
            @Override public Set<String> capabilities() { return Set.of(); }
            @Override public Optional<RoutingPolicy> policy(String capability) { return Optional.empty(); }
        };
        assertThat(empty.isKnown("code-analysis")).isFalse();
    }

    @Test
    void isKnownDefaultThrowsNullPointerExceptionForNull() {
        assertThatThrownBy(() -> minimal.isKnown(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("capability");
    }
}
