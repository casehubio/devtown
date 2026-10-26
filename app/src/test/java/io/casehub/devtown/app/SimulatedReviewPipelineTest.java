package io.casehub.devtown.app;

import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgentRegistry;
import io.casehub.devtown.review.ReviewerOutcome;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.config.ManifestResult;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(AgentPipelineTestProfile.class)
class SimulatedReviewPipelineTest {

    @Inject
    AgentRegistry               agentRegistry;
    @Inject
    ReviewerAgentRegistry       reviewerAgentRegistry;
    @Inject
    Instance<ManifestResult>    manifestResult;
    @Inject
    AgentProvider               agentProvider;
    @Inject
    Instance<CodeAnalysisAgent> codeAnalysisAgent;

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
    void manifestAliasResolutionAvailable() {
        assertThat(manifestResult.isResolvable())
                .as("TestManifestResultProducer should provide ManifestResult")
                .isTrue();

        var manifest = manifestResult.get();
        assertThat(manifest.aliases())
                .as("Aliases map descriptor modelRefs to simulation chains")
                .containsKeys("reasoning-heavy", "standard-review", "cheap-fast");
        assertThat(manifest.defaultBackendKey()).isEqualTo("simulation");
    }

    @Test
    void aliasResolutionRoutesThroughAgentProvider() {
        var config = AgentSessionConfig.of("Test prompt", "Test input")
                                       .withModel("reasoning-heavy");
        var events = agentProvider.invoke(config)
                                  .collect().asList()
                                  .await().atMost(Duration.ofSeconds(5));

        assertThat(events)
                .as("RoutingAgentProvider should resolve 'reasoning-heavy' alias to simulation backend")
                .isNotEmpty();
        assertThat(events).anyMatch(e -> e instanceof AgentEvent.TextDelta);
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

    @Test
    void codeAnalysisAgentCompletesWithSimulationBackend() {
        assertThat(codeAnalysisAgent.isResolvable())
                .as("DescriptorDrivenCodeAnalysisAgent should be CDI-resolvable")
                .isTrue();

        var pr = new PrPayload(
                "casehubio/devtown", 102, "def456abc", "main",
                50, "test-contributor", -1L,
                List.of("src/auth/TokenService.java"));
        var diff = new PrDiff(
                "casehubio/devtown", 102, "base456", "def456abc",
                List.of(new PrDiff.FileDiff(
                        "src/auth/TokenService.java", "modified",
                        "@@ -1,3 +1,5 @@\n+import io.auth.*;\n public class TokenService {}",
                        2, 0)),
                false);

        CodeAnalysisResult result = codeAnalysisAgent.get().analyse(new ReviewContext(pr, diff));

        assertThat(result).isNotNull();
        assertThat(result.complete()).isTrue();
        assertThat(result.securitySensitive())
                .as("Auth file changes should be flagged as security-sensitive")
                .isTrue();
    }

    @Test
    void codeAnalysisResultSerializationMatchesBindingPaths() throws Exception {
        var result = new CodeAnalysisResult(true, true, true, "cross-module",
                                            List.of("src/api/Gateway.java"), List.of("api-boundary"));

        var mapper = new ObjectMapper();
        var json   = mapper.valueToTree(result);

        assertThat(json.get("securitySensitive").asBoolean())
                .as("pr-review.yaml binding: .codeAnalysis.securitySensitive")
                .isTrue();
        assertThat(json.get("architectureCrossing").asBoolean())
                .as("pr-review.yaml binding: .codeAnalysis.architectureCrossing")
                .isTrue();
        assertThat(json.get("scope").asText()).isEqualTo("cross-module");
        assertThat(json.get("flaggedFiles").isArray()).isTrue();
    }
}
