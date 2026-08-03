package io.casehub.devtown.app.routing;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.ImplementationCandidate;
import io.casehub.api.spi.routing.ImplementationRoutingContext;
import io.casehub.api.spi.routing.ImplementationSelection;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustWeightedImplementationRoutingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CbrReviewerMatchingIntegrationTest {

    private static final TrustRoutingPolicy CBR_POLICY =
            new TrustRoutingPolicy(0.7, 5, 0.1, 0.6, Map.of(), false, null, Set.of(), 0.2);

    @Test
    void higherTrustAgent_winsInPureTrustRouting() {
        var ctx = new ImplementationRoutingContext(UUID.randomUUID(), ReviewDomain.SECURITY_REVIEW,
                NullNode.instance, "test-tenant", List.of());

        var trustSource = new StubTrustScoreSource(
                Map.of("agent-high|security-review", 0.90,
                       "agent-low|security-review", 0.75),
                Map.of("agent-high|security-review", 15,
                       "agent-low|security-review", 15));

        var classifier = new TrustCandidateClassifier();
        var strategy = new TrustWeightedImplementationRoutingStrategy(classifier, trustSource, new StubPolicyProvider(CBR_POLICY));

        var candidates = List.of(
                new ImplementationCandidate("binding-high", "agent-high", ReviewDomain.SECURITY_REVIEW),
                new ImplementationCandidate("binding-low", "agent-low", ReviewDomain.SECURITY_REVIEW));

        var result = strategy.select(ctx, candidates);

        assertThat(result).isInstanceOf(ImplementationSelection.Selected.class);
        var selected = (ImplementationSelection.Selected) result;
        assertThat(selected.bindingNames()).containsExactly("binding-high");
    }

    @Test
    void zeroPrecedents_identicalToPureTrustRouting() {
        var ctx = new ImplementationRoutingContext(UUID.randomUUID(), ReviewDomain.SECURITY_REVIEW,
                NullNode.instance, "test-tenant", List.of());

        var policyZero = new TrustRoutingPolicy(0.7, 5, 0.1, 0.6, Map.of(), false, null, Set.of(), 0.0);

        var trustSource = new StubTrustScoreSource(
                Map.of("agent-a|security-review", 0.90,
                       "agent-b|security-review", 0.85),
                Map.of("agent-a|security-review", 15,
                       "agent-b|security-review", 15));

        var classifier = new TrustCandidateClassifier();

        var strategyWithCbr = new TrustWeightedImplementationRoutingStrategy(
                classifier, trustSource, new StubPolicyProvider(CBR_POLICY));
        var strategyWithout = new TrustWeightedImplementationRoutingStrategy(
                classifier, trustSource, new StubPolicyProvider(policyZero));

        var candidates = List.of(
                new ImplementationCandidate("binding-a", "agent-a", ReviewDomain.SECURITY_REVIEW),
                new ImplementationCandidate("binding-b", "agent-b", ReviewDomain.SECURITY_REVIEW));

        var resultWith = strategyWithCbr.select(ctx, candidates);
        var resultWithout = strategyWithout.select(ctx, candidates);

        assertThat(((ImplementationSelection.Selected) resultWith).bindingNames())
                .isEqualTo(((ImplementationSelection.Selected) resultWithout).bindingNames());
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
