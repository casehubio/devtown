package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.RoutingPolicy;
import io.casehub.devtown.domain.spi.CapabilityRegistry;
import io.casehub.ledger.core.trust.TrustGateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

@ApplicationScoped
public class ContributorTrustResolver {

    private final CapabilityRegistry capabilityRegistry;
    private final TrustGateService trustGateService;

    @Inject
    public ContributorTrustResolver(CapabilityRegistry capabilityRegistry,
                                     TrustGateService trustGateService) {
        this.capabilityRegistry = capabilityRegistry;
        this.trustGateService = trustGateService;
    }

    public OptionalDouble resolveScore(String contributorId, String domain) {
        String                  domainCap = ContributorTrustCapability.forDomain(domain);
        Map<String, Double>     scores    = trustGateService.allCapabilityScores(contributorId);
        Optional<RoutingPolicy> policy    = capabilityRegistry.policy(domainCap);

        if (policy.isPresent() && policy.get().minimumObservations().isPresent()) {
            int observations = trustGateService.decisionCount(contributorId, domainCap);
            if (observations >= policy.get().minimumObservations().getAsInt()) {
                Double domainScore = scores.get(domainCap);
                if (domainScore != null) {
                    return OptionalDouble.of(domainScore);
                }
            }
        }

        Double aggregateScore = scores.get(ContributorTrustCapability.PR_CONTRIBUTION);
        return aggregateScore != null ? OptionalDouble.of(aggregateScore) : OptionalDouble.empty();
    }
}
