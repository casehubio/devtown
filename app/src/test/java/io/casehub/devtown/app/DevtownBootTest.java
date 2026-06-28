package io.casehub.devtown.app;

import io.casehub.devtown.domain.spi.CapabilityRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DevtownBootTest {

    @Inject
    CapabilityRegistry capabilityRegistry;

    @Test
    void applicationBoots() {
    }

    @Test
    void capabilityRegistryIsDiscoverableViaCdi() {
        assertThat(capabilityRegistry).isNotNull();
        assertThat(capabilityRegistry.capabilities()).hasSize(14);
    }
}
