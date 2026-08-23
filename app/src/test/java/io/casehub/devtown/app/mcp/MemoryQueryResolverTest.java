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
}
