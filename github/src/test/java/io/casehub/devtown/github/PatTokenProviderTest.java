package io.casehub.devtown.github;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PatTokenProviderTest {

    @Test
    void returnsConfiguredToken() {
        var provider = new PatTokenProvider("ghp_test123");
        assertThat(provider.getToken()).isEqualTo("ghp_test123");
    }

    @Test
    void returnsEmptyWhenNotConfigured() {
        var provider = new PatTokenProvider("");
        assertThat(provider.getToken()).isEmpty();
    }
}
