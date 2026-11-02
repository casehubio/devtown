package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.DevtownCapabilityRegistry;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.spi.CapabilityRegistry;
import io.casehub.ledger.core.trust.TrustGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContributorTrustResolverTest {

    private TrustGateService trustGateService;
    private ContributorTrustResolver resolver;

    @BeforeEach
    void setUp() {
        trustGateService = mock(TrustGateService.class);
        CapabilityRegistry capabilityRegistry = new DevtownCapabilityRegistry();
        resolver = new ContributorTrustResolver(capabilityRegistry, trustGateService);
    }

    @Test
    void aboveThreshold_returnsDomainScore() {
        String domainCap = ContributorTrustCapability.forDomain(ReviewDomain.SECURITY_REVIEW);
        when(trustGateService.decisionCount("alice", domainCap)).thenReturn(12);
        when(trustGateService.allCapabilityScores("alice")).thenReturn(
            Map.of(domainCap, 0.85, ContributorTrustCapability.PR_CONTRIBUTION, 0.70));

        OptionalDouble result = resolver.resolveScore("alice", ReviewDomain.SECURITY_REVIEW);
        assertThat(result).hasValue(0.85);
    }

    @Test
    void belowThreshold_fallsBackToAggregate() {
        String domainCap = ContributorTrustCapability.forDomain(ReviewDomain.SECURITY_REVIEW);
        when(trustGateService.decisionCount("alice", domainCap)).thenReturn(3);
        when(trustGateService.allCapabilityScores("alice")).thenReturn(
            Map.of(domainCap, 0.85, ContributorTrustCapability.PR_CONTRIBUTION, 0.70));

        OptionalDouble result = resolver.resolveScore("alice", ReviewDomain.SECURITY_REVIEW);
        assertThat(result).hasValue(0.70);
    }

    @Test
    void noDomainScore_fallsBackToAggregate() {
        String domainCap = ContributorTrustCapability.forDomain(ReviewDomain.SECURITY_REVIEW);
        when(trustGateService.decisionCount("alice", domainCap)).thenReturn(15);
        when(trustGateService.allCapabilityScores("alice")).thenReturn(
            Map.of(ContributorTrustCapability.PR_CONTRIBUTION, 0.70));

        OptionalDouble result = resolver.resolveScore("alice", ReviewDomain.SECURITY_REVIEW);
        assertThat(result).hasValue(0.70);
    }

    @Test
    void noScoreAtAll_returnsEmpty() {
        String domainCap = ContributorTrustCapability.forDomain(ReviewDomain.SECURITY_REVIEW);
        when(trustGateService.decisionCount("alice", domainCap)).thenReturn(0);
        when(trustGateService.allCapabilityScores("alice")).thenReturn(Map.of());

        OptionalDouble result = resolver.resolveScore("alice", ReviewDomain.SECURITY_REVIEW);
        assertThat(result).isEmpty();
    }

    @Test
    void styleReview_lowerThreshold_uses5Observations() {
        String domainCap = ContributorTrustCapability.forDomain(ReviewDomain.STYLE_REVIEW);
        when(trustGateService.decisionCount("alice", domainCap)).thenReturn(5);
        when(trustGateService.allCapabilityScores("alice")).thenReturn(
            Map.of(domainCap, 0.90, ContributorTrustCapability.PR_CONTRIBUTION, 0.70));

        OptionalDouble result = resolver.resolveScore("alice", ReviewDomain.STYLE_REVIEW);
        assertThat(result).hasValue(0.90);
    }
}
