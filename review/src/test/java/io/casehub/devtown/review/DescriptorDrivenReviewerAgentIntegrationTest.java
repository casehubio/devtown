package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorDrivenReviewerAgentIntegrationTest {

    @Test
    void fullPath_descriptorToFindings() {
        String simulatedResponse = """
            {"findings": [{"severity": "HIGH", "category": "credential-exposure",
             "filePath": "src/auth/TokenService.java",
             "lineRange": {"startLine": 42, "endLine": 45},
             "message": "Potential credential exposure",
             "confidence": 0.85}]}""";

        var descriptor = AgentDescriptor.builder()
            .agentId("security-reviewer")
            .name("Security Review Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(
                AgentCapability.builder()
                    .name("security-review")
                    .modelRef("reasoning-heavy")
                    .build()))
            .extensionData(Map.of("systemPrompt",
                "You are a security code reviewer. Analyze the code diff."))
            .build();

        var registry = new StubRegistry(descriptor);
        var provider = mockProvider(simulatedResponse);

        var agent = new DescriptorDrivenReviewerAgent(
            provider, registry, "security-reviewer", "security-review");

        var diff = new PrDiff("org/repo", 1, "base", "head",
            List.of(new PrDiff.FileDiff("src/auth/TokenService.java", "modified",
                "@@ -42,6 +42,10 @@\n+    return jwt;", 4, 0)),
            false);
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var completed = (ReviewerOutcome.Completed) outcome;
        assertEquals(1, completed.findings().size());
        assertEquals(ReviewFinding.Severity.HIGH, completed.findings().get(0).severity());
        assertEquals("credential-exposure", completed.findings().get(0).category());
        assertEquals("src/auth/TokenService.java", completed.findings().get(0).filePath());
    }

    @Test
    void fullPath_emptyFindings() {
        var descriptor = AgentDescriptor.builder()
            .agentId("style-reviewer")
            .name("Style Review Agent")
            .slot("reviewer")
            .tenancyId("default")
            .capabilities(List.of(
                AgentCapability.builder().name("style-review").build()))
            .extensionData(Map.of("systemPrompt", "You are a style reviewer."))
            .build();

        var registry = new StubRegistry(descriptor);
        var provider = mockProvider("{\"findings\": []}");

        var agent = new DescriptorDrivenReviewerAgent(
            provider, registry, "style-reviewer", "style-review");

        var diff = new PrDiff("org/repo", 1, "base", "head",
            List.of(new PrDiff.FileDiff("src/Helper.java", "modified",
                "@@ -1,3 +1,5 @@\n+    return x;", 2, 0)),
            false);
        var context = new ReviewContext(samplePr(), diff);

        var outcome = agent.handle(context);

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        assertTrue(((ReviewerOutcome.Completed) outcome).findings().isEmpty());
    }

    @Test
    void registryProducesCorrectAgents() {
        var secDesc = buildDescriptor("security-reviewer", "security-review",
            "You are a security reviewer.");
        var archDesc = buildDescriptor("architecture-reviewer", "architecture-review",
            "You are an architecture reviewer.");

        var registry = new MultiDescriptorRegistry(List.of(secDesc, archDesc));
        var provider = mockProvider("{\"findings\": []}");

        var secAgent = new DescriptorDrivenReviewerAgent(
            provider, registry, "security-reviewer", "security-review");
        var archAgent = new DescriptorDrivenReviewerAgent(
            provider, registry, "architecture-reviewer", "architecture-review");

        var agents = ReviewerAgentRegistry.of(List.of(secAgent, archAgent));

        assertTrue(agents.forCapability("security-review").isPresent());
        assertTrue(agents.forCapability("architecture-review").isPresent());
        assertEquals(2, agents.all().size());
    }

    private static PrPayload samplePr() {
        return new PrPayload("org/repo", 1, "head", "main", 100, "dev", 1L, List.of());
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

    private static AgentProvider mockProvider(String jsonResponse) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().items(
                    new AgentEvent.TextDelta(jsonResponse),
                    new AgentEvent.InvocationComplete(
                        0, 0, 0, 0, 0, null, 100L, 80L, null, 1, false));
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static class StubRegistry implements AgentRegistry {
        private final AgentDescriptor d;
        StubRegistry(AgentDescriptor d) { this.d = d; }

        @Override public void register(AgentDescriptor desc) {}
        @Override public Optional<AgentDescriptor> findById(String id, String t) {
            return d.agentId().equals(id) ? Optional.of(d) : Optional.empty();
        }
        @Override public List<AgentMatch> find(AgentQuery q) { return List.of(); }
    }

    static class MultiDescriptorRegistry implements AgentRegistry {
        private final List<AgentDescriptor> descriptors;
        MultiDescriptorRegistry(List<AgentDescriptor> d) { this.descriptors = d; }

        @Override public void register(AgentDescriptor desc) {}
        @Override public Optional<AgentDescriptor> findById(String id, String t) {
            return descriptors.stream()
                .filter(d -> d.agentId().equals(id))
                .findFirst();
        }
        @Override public List<AgentMatch> find(AgentQuery q) { return List.of(); }
    }
}
