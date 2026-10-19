package io.casehub.devtown.review;

import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorDrivenReviewerAgentTest {

    @Test
    void readsCapabilityAndAgentIdFromConstructor() {
        var descriptor = buildDescriptor("sec-agent", "security-review",
            "You are a security reviewer.");
        var registry = new SingleDescriptorRegistry(descriptor);

        var agent = new DescriptorDrivenReviewerAgent(
            null, registry, "sec-agent", "security-review");

        assertEquals("security-review", agent.capability());
    }

    @Test
    void systemPromptBody_readsFromExtensionData() {
        var descriptor = buildDescriptor("sec-agent", "security-review",
            "You are a security reviewer.");
        var registry = new SingleDescriptorRegistry(descriptor);

        var agent = new DescriptorDrivenReviewerAgent(
            null, registry, "sec-agent", "security-review");

        assertEquals("You are a security reviewer.", agent.systemPromptBody());
    }

    @Test
    void systemPromptBody_throwsWhenMissing() {
        var descriptor = AgentDescriptor.builder()
            .agentId("no-prompt")
            .name("No Prompt Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(AgentCapability.builder().name("test").build()))
            .build();
        var registry = new SingleDescriptorRegistry(descriptor);

        var agent = new DescriptorDrivenReviewerAgent(
            null, registry, "no-prompt", "test");

        assertThrows(IllegalStateException.class, agent::systemPromptBody);
    }

    private static AgentDescriptor buildDescriptor(
            String agentId, String capName, String prompt) {
        return AgentDescriptor.builder()
            .agentId(agentId)
            .name(agentId)
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(AgentCapability.builder().name(capName).build()))
            .extensionData(Map.of("systemPrompt", prompt))
            .build();
    }

    static class SingleDescriptorRegistry implements AgentRegistry {
        private final AgentDescriptor descriptor;
        SingleDescriptorRegistry(AgentDescriptor d) { this.descriptor = d; }

        @Override public void register(AgentDescriptor d) {}
        @Override public Optional<AgentDescriptor> findById(String id, String tenancy) {
            return descriptor.agentId().equals(id) ? Optional.of(descriptor) : Optional.empty();
        }
        @Override public List<AgentMatch> find(AgentQuery q) { return List.of(); }
    }
}
