package io.casehub.devtown.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevtownModelEnricherTest {

    @Test
    void summaryIsNonBlank() {
        var enricher = new DevtownModelEnricher();
        assertThat(enricher.summary()).isNotBlank();
    }

    @Test
    void stateContainsReasoningDomain() {
        var enricher = new DevtownModelEnricher();
        assertThat(enricher.state()).containsKey("reasoningDomain");
        assertThat(enricher.state().get("reasoningDomain")).isEqualTo("worker-reasoning");
    }
}
