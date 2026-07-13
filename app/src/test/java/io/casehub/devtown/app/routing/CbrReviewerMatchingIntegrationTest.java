package io.casehub.devtown.app.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustWeightedAgentStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

class CbrReviewerMatchingIntegrationTest {

    private static final TrustRoutingPolicy CBR_POLICY =
            new TrustRoutingPolicy(0.7, 5, 0.1, 0.6, Map.of(), false, null, Set.of(), 0.2);

    @Test
    void agentWithLowerTrustButHigherPrecedentMatch_winsOverHigherTrustNoPrecedent() {
        var experiences = List.of(new RetrievedExperience(
                "security fix in auth module", "reviewed auth changes", "COMPLETED", 1.0, 0.85,
                Map.of(), List.of(new ExperiencePlanStep(
                "security-review-binding", ReviewDomain.SECURITY_REVIEW,
                "specialist-agent", "SUCCESS", 0, Map.of())),
                Map.of()));

        var ctx = new AgentRoutingContext(UUID.randomUUID(), ReviewDomain.SECURITY_REVIEW,
                NullNode.instance, "test-tenant", experiences);

        var trustSource = new StubTrustScoreSource(
                Map.of("specialist-agent|security-review", 0.85,
                       "generalist-agent|security-review", 0.87),
                Map.of("specialist-agent|security-review", 15,
                       "generalist-agent|security-review", 15));

        var classifier = new TrustCandidateClassifier();
        var strategy = new TrustWeightedAgentStrategy(classifier, trustSource, new StubPolicyProvider(CBR_POLICY));

        var candidates = List.of(
                new AgentCandidate("specialist-agent", Set.of(ReviewDomain.SECURITY_REVIEW),
                        0, AgentHealth.READY, null, null),
                new AgentCandidate("generalist-agent", Set.of(ReviewDomain.SECURITY_REVIEW),
                        0, AgentHealth.READY, null, null));

        var result = strategy.select(ctx, candidates).await().indefinitely();

        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
        var selected = (RoutingResult.Selected) result;
        assertThat(selected.single().executorId()).isEqualTo("specialist-agent");
        assertThat(selected.single().reason()).contains("cbr_bonus");
    }

    @Test
    void zeroPrecedents_identicalToPureTrustRouting() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), ReviewDomain.SECURITY_REVIEW,
                NullNode.instance, "test-tenant", List.of());

        var policyZero = new TrustRoutingPolicy(0.7, 5, 0.1, 0.6, Map.of(), false, null, Set.of(), 0.0);

        var trustSource = new StubTrustScoreSource(
                Map.of("agent-a|security-review", 0.90,
                       "agent-b|security-review", 0.85),
                Map.of("agent-a|security-review", 15,
                       "agent-b|security-review", 15));

        var classifier = new TrustCandidateClassifier();

        var strategyWithCbr = new TrustWeightedAgentStrategy(
                classifier, trustSource, new StubPolicyProvider(CBR_POLICY));
        var strategyWithout = new TrustWeightedAgentStrategy(
                classifier, trustSource, new StubPolicyProvider(policyZero));

        var candidates = List.of(
                new AgentCandidate("agent-a", Set.of(ReviewDomain.SECURITY_REVIEW),
                        0, AgentHealth.READY, null, null),
                new AgentCandidate("agent-b", Set.of(ReviewDomain.SECURITY_REVIEW),
                        0, AgentHealth.READY, null, null));

        var resultWith = strategyWithCbr.select(ctx, candidates).await().indefinitely();
        var resultWithout = strategyWithout.select(ctx, candidates).await().indefinitely();

        assertThat(((RoutingResult.Selected) resultWith).single().executorId())
                .isEqualTo(((RoutingResult.Selected) resultWithout).single().executorId());
    }

    private record StubTrustScoreSource(
            Map<String, Double> scores,
            Map<String, Integer> counts
    ) implements TrustScoreSource {
        @Override
        public OptionalDouble globalScore(String workerId) { return OptionalDouble.empty(); }

        @Override
        public OptionalDouble capabilityScore(String workerId, String capability) {
            Double s = scores.get(workerId + "|" + capability);
            return s != null ? OptionalDouble.of(s) : OptionalDouble.empty();
        }

        @Override
        public OptionalDouble dimensionScore(String workerId, String dimension) {
            return OptionalDouble.empty();
        }

        @Override
        public OptionalDouble capabilityDimensionScore(String workerId, String capability, String dimension) {
            return OptionalDouble.empty();
        }

        @Override
        public int decisionCount(String workerId, String capability) {
            return counts.getOrDefault(workerId + "|" + capability, 0);
        }

        @Override
        public Map<String, Double> allCapabilityScores(String workerId) { return Map.of(); }

        @Override
        public Map<String, Double> allDimensionScores(String workerId) { return Map.of(); }

        @Override
        public Map<String, Double> qualityScores(String workerId, String capability) { return Map.of(); }
    }

    private record StubPolicyProvider(TrustRoutingPolicy policy) implements TrustRoutingPolicyProvider {
        @Override
        public String id()                                             {return "stub";}

        @Override
        public TrustRoutingPolicy forCapability(String capabilityName) {return policy;}
    }

}
