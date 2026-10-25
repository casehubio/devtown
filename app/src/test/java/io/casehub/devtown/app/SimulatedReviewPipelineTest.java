package io.casehub.devtown.app;

import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgentRegistry;
import io.casehub.devtown.review.ReviewerOutcome;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(AgentPipelineTestProfile.class)
class SimulatedReviewPipelineTest {

    @Inject AgentRegistry agentRegistry;
    @Inject ReviewerAgentRegistry reviewerAgentRegistry;

    @Test
    void eidosLoadsReviewerDescriptors() {
        var matches = agentRegistry.find(AgentQuery.bySlot("reviewer", "default"));
        assertThat(matches)
            .as("descriptors.yaml defines 6 reviewer agents")
            .hasSize(6);
    }

    @Test
    void descriptorDrivenAgentsResolvedFromRegistry() {
        var agents = reviewerAgentRegistry.all();
        assertThat(agents)
            .as("DescriptorDrivenReviewerAgentProducer creates agents for findings capabilities")
            .isNotEmpty();

        var capabilities = agents.stream()
            .map(a -> a.capability())
            .toList();
        assertThat(capabilities).contains("security-review");
    }

    @Test
    void simulationBackendProducesStructuredFindings() {
        var agent = reviewerAgentRegistry.forCapability("security-review")
            .orElseThrow(() -> new AssertionError("No agent for security-review"));

        var pr = new PrPayload(
            "casehubio/devtown", 101, "abc123def", "main",
            150, "test-contributor", -1L,
            List.of("src/auth/TokenService.java"));

        var diff = new PrDiff(
            "casehubio/devtown", 101, "base123", "abc123def",
            List.of(new PrDiff.FileDiff(
                "src/auth/TokenService.java", "modified",
                "@@ -1,5 +1,10 @@\n+import io.jsonwebtoken.Jwts;\n public class TokenService {\n+    public String createToken() {\n+        return Jwts.builder().compact();\n+    }\n }",
                5, 0)),
            false);

        ReviewerOutcome outcome = agent.handle(new ReviewContext(pr, diff));

        assertThat(outcome)
            .as("Simulation backend should return a completed outcome")
            .isInstanceOf(ReviewerOutcome.Completed.class);

        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertThat(findings).isNotEmpty();
        assertThat(findings.get(0).message()).contains("token exposure");
    }
}
