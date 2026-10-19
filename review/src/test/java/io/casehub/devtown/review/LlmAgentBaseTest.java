package io.casehub.devtown.review;

import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LlmAgentBaseTest {

    @Test
    void resolveSystemPrompt_returnsPromptFromExtensionData() {
        var descriptor = AgentDescriptor.builder()
            .agentId("test-agent")
            .name("Test Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(
                AgentCapability.builder().name("test-cap").build()))
            .extensionData(Map.of("systemPrompt", "You are a test reviewer."))
            .build();

        var registry = new StubAgentRegistry(descriptor);
        var agent = new TestLlmAgent(null, registry, "test-agent", "test-cap");

        assertEquals("You are a test reviewer.", agent.resolveSystemPrompt());
    }

    @Test
    void resolveSystemPrompt_returnsNull_whenNoExtensionData() {
        var descriptor = AgentDescriptor.builder()
            .agentId("test-agent")
            .name("Test Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(
                AgentCapability.builder().name("test-cap").build()))
            .build();

        var registry = new StubAgentRegistry(descriptor);
        var agent = new TestLlmAgent(null, registry, "test-agent", "test-cap");

        assertNull(agent.resolveSystemPrompt());
    }

    @Test
    void resolveSystemPrompt_returnsNull_whenRegistryIsNull() {
        var agent = new TestLlmAgent(null, null, "test-agent", "test-cap");
        assertNull(agent.resolveSystemPrompt());
    }

    @Test
    void resolveSystemPrompt_returnsNull_whenAgentNotFound() {
        var descriptor = AgentDescriptor.builder()
            .agentId("other-agent")
            .name("Other Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(
                AgentCapability.builder().name("test-cap").build()))
            .extensionData(Map.of("systemPrompt", "irrelevant"))
            .build();

        var registry = new StubAgentRegistry(descriptor);
        var agent = new TestLlmAgent(null, registry, "test-agent", "test-cap");

        assertNull(agent.resolveSystemPrompt());
    }

    static class TestLlmAgent extends LlmAgentBase {
        private final AgentProvider provider;
        private final AgentRegistry registry;
        private final String agentId;
        private final String capability;

        TestLlmAgent(AgentProvider provider, AgentRegistry registry,
                     String agentId, String capability) {
            this.provider = provider;
            this.registry = registry;
            this.agentId = agentId;
            this.capability = capability;
        }

        @Override protected AgentProvider agentProvider() { return provider; }
        @Override protected AgentRegistry agentRegistry() { return registry; }
        @Override protected String agentId() { return agentId; }
        @Override public String capability() { return capability; }
        @Override protected String systemPromptBody() { return resolveSystemPrompt(); }
    }

    static class StubAgentRegistry implements AgentRegistry {
        private final AgentDescriptor descriptor;
        StubAgentRegistry(AgentDescriptor descriptor) { this.descriptor = descriptor; }

        @Override public void register(AgentDescriptor d) {}
        @Override public Optional<AgentDescriptor> findById(String id, String tenancy) {
            return descriptor.agentId().equals(id) ? Optional.of(descriptor) : Optional.empty();
        }
        @Override public List<AgentMatch> find(AgentQuery query) {
            return List.of();
        }
    }
}
