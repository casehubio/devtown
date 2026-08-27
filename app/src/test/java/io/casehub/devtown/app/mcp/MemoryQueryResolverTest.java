package io.casehub.devtown.app.mcp;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MemoryQueryResolverTest {

    @Mock Instance<CaseMemoryStore> memoryStoreInstance;
    @Mock CurrentPrincipal principal;
    @Mock io.casehub.devtown.app.trust.EvidentialViolationStore violationStore;
    @Mock io.casehub.devtown.app.CbrWeightOverrideStore cbrWeightOverrides;
    @Mock Instance<io.casehub.devtown.review.CbrRetrievalService> cbrRetrievalService;

    @InjectMocks MemoryQueryResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(principal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void priorDecisions_storeNotResolvable_returnsEmptyList() {
        when(memoryStoreInstance.isResolvable()).thenReturn(false);

        List<MemoryQueryResolver.PriorDecision> decisions = resolver.priorDecisions(
                "casehubio/devtown", "src/Main.java");

        assertThat(decisions).isEmpty();
    }

    @Test
    void similarCases_notResolvable_returnsEmptyList() {
        when(cbrRetrievalService.isResolvable()).thenReturn(false);

        var results = resolver.similarCases("casehubio/devtown", 42, "alice", 100, "src/Main.java");

        assertThat(results).isEmpty();
    }

    @Test
    void similarCases_mapsPrecedentToLocalRecords() {
        when(cbrRetrievalService.isResolvable()).thenReturn(true);
        var mockService = org.mockito.Mockito.mock(io.casehub.devtown.review.CbrRetrievalService.class);
        when(cbrRetrievalService.get()).thenReturn(mockService);

        java.util.UUID caseId = java.util.UUID.randomUUID();
        var precedent = new io.casehub.devtown.domain.cbr.Precedent(
                caseId,
                new io.casehub.devtown.domain.cbr.SimilarityScore(0.85, java.util.Map.of("repo", 1.0, "modules", 0.7)),
                io.casehub.devtown.domain.cbr.PrFeatureVector.from("casehubio/devtown", 42, "alice", 100, List.of("src/Main.java")),
                "APPROVED",
                java.util.Map.of("code-analysis", new io.casehub.devtown.domain.cbr.CapabilityOutcome("COMPLETED", "approved", java.time.Duration.ofMinutes(5))),
                java.time.Duration.ofMinutes(10));

        when(mockService.findSimilar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("casehubio/devtown"),
                                     org.mockito.ArgumentMatchers.eq(io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID)))
                .thenReturn(List.of(precedent));

        var results = resolver.similarCases("casehubio/devtown", 42, "alice", 100, "src/Main.java");

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.caseId()).isEqualTo(caseId);
        assertThat(result.similarity().score()).isEqualTo(0.85);
        assertThat(result.similarity().breakdown()).containsEntry("repo", 1.0);
        assertThat(result.vector().repo()).isEqualTo("casehubio/devtown");
        assertThat(result.vector().prNumber()).isEqualTo(42);
        assertThat(result.vector().hasTests()).isFalse();
        assertThat(result.outcome()).isEqualTo("APPROVED");
        assertThat(result.capabilityOutcomes()).containsKey("code-analysis");
        assertThat(result.capabilityOutcomes().get("code-analysis").outcome()).isEqualTo("COMPLETED");
        assertThat(result.completionTime()).isEqualTo(java.time.Duration.ofMinutes(10));
    }

}
