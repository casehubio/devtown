package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PrReviewReasoningEnrichmentTest {

    @Inject
    CaseDefinitionRegistry registry;

    @Test
    void prReviewDefinition_configuresCaseScopedReasoningDomain() {
        var definition = registry.findByName("pr-review");
        assertThat(definition).isPresent();

        var memoryConfig = definition.get().getMemoryRetrieval();
        assertThat(memoryConfig).isNotNull();
        assertThat(memoryConfig.enabled()).isTrue();
        assertThat(memoryConfig.caseScopedDomains()).contains("worker-reasoning");
        assertThat(memoryConfig.maxCaseMemories()).isGreaterThan(0);
    }

    @Test
    void prReviewDefinition_preservesStandardMemoryDomains() {
        var definition = registry.findByName("pr-review").orElseThrow();
        var memoryConfig = definition.getMemoryRetrieval();

        assertThat(memoryConfig.domains()).contains("experience", "reflection");
    }
}
