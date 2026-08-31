package io.casehub.devtown.app.mcp;

import io.casehub.devtown.domain.memory.DevtownMemoryDomain;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReasoningQueryResolverTest {

    @Mock Instance<CaseMemoryStore> memoryStoreInstance;
    @Mock CaseMemoryStore memoryStore;
    @Mock CurrentPrincipal principal;

    @InjectMocks ReasoningQueryResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(principal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
        when(memoryStoreInstance.isResolvable()).thenReturn(true);
        when(memoryStoreInstance.get()).thenReturn(memoryStore);
    }

    @Test
    void moduleReasoning_storeNotResolvable_returnsEmptyList() {
        when(memoryStoreInstance.isResolvable()).thenReturn(false);

        var results = resolver.moduleReasoning("casehubio/devtown", "app", 20);

        assertThat(results).isEmpty();
    }

    @Test
    void moduleReasoning_noModuleFacts_returnsEmptyList() {
        when(memoryStore.query(any())).thenReturn(List.of());

        var results = resolver.moduleReasoning("casehubio/devtown", "app", 20);

        assertThat(results).isEmpty();
    }

    @Test
    void moduleReasoning_queriesModuleEntityThenReasoningByCaseId() {
        UUID caseId = UUID.randomUUID();
        var moduleFact = new Memory("m1", "module:casehubio/devtown/app",
                DevtownMemoryDomain.SOFTWARE_REVIEW, TenancyConstants.DEFAULT_TENANT_ID,
                caseId.toString(), "Code review on app module", Map.of("capability", "code-analysis"),
                Instant.now(), null, null, null, null);
        var reasoningTrace = new Memory("r1", "case:" + caseId,
                DevtownMemoryDomain.WORKER_REASONING, TenancyConstants.DEFAULT_TENANT_ID,
                caseId.toString(), "Found potential null dereference in FooService",
                Map.of("workerName", "claude-reviewer", "capability", "code-analysis", "outcome", "FLAGGED"),
                Instant.now(), null, null, null, null);

        var queryCaptor = ArgumentCaptor.forClass(MemoryQuery.class);
        when(memoryStore.query(any()))
                .thenReturn(List.of(moduleFact))
                .thenReturn(List.of(reasoningTrace));

        var results = resolver.moduleReasoning("casehubio/devtown", "app", 20);

        assertThat(results).hasSize(1);
        var trace = results.get(0);
        assertThat(trace.workerName()).isEqualTo("claude-reviewer");
        assertThat(trace.capability()).isEqualTo("code-analysis");
        assertThat(trace.outcome()).isEqualTo("FLAGGED");
        assertThat(trace.reasoning()).isEqualTo("Found potential null dereference in FooService");
        assertThat(trace.module()).isEqualTo("app");
        assertThat(trace.repo()).isEqualTo("casehubio/devtown");

        verify(memoryStore, org.mockito.Mockito.times(2)).query(queryCaptor.capture());
        var queries = queryCaptor.getAllValues();
        assertThat(queries.get(0).entityIds()).containsExactly("module:casehubio/devtown/app");
        assertThat(queries.get(0).domain()).isEqualTo(DevtownMemoryDomain.SOFTWARE_REVIEW);
        assertThat(queries.get(1).entityIds()).containsExactly("case:" + caseId);
        assertThat(queries.get(1).domain()).isEqualTo(DevtownMemoryDomain.WORKER_REASONING);
    }

    @Test
    void moduleReasoning_multipleCases_deduplicatesCaseIds() {
        UUID caseId = UUID.randomUUID();
        var fact1 = new Memory("m1", "module:casehubio/devtown/app",
                DevtownMemoryDomain.SOFTWARE_REVIEW, TenancyConstants.DEFAULT_TENANT_ID,
                caseId.toString(), "Review 1", Map.of(), Instant.now(), null, null, null, null);
        var fact2 = new Memory("m2", "module:casehubio/devtown/app",
                DevtownMemoryDomain.SOFTWARE_REVIEW, TenancyConstants.DEFAULT_TENANT_ID,
                caseId.toString(), "Review 2", Map.of(), Instant.now(), null, null, null, null);

        when(memoryStore.query(any()))
                .thenReturn(List.of(fact1, fact2))
                .thenReturn(List.of());

        resolver.moduleReasoning("casehubio/devtown", "app", 20);

        var queryCaptor = ArgumentCaptor.forClass(MemoryQuery.class);
        verify(memoryStore, org.mockito.Mockito.times(2)).query(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(1).entityIds()).hasSize(1);
    }

    @Test
    void moduleReasoning_nullCaseIds_filtered() {
        var factNullCase = new Memory("m1", "module:casehubio/devtown/app",
                DevtownMemoryDomain.SOFTWARE_REVIEW, TenancyConstants.DEFAULT_TENANT_ID,
                null, "Review with no case", Map.of(), Instant.now(), null, null, null, null);

        when(memoryStore.query(any())).thenReturn(List.of(factNullCase));

        var results = resolver.moduleReasoning("casehubio/devtown", "app", 20);

        assertThat(results).isEmpty();
    }

    @Test
    void caseReasoning_storeNotResolvable_returnsEmptyList() {
        when(memoryStoreInstance.isResolvable()).thenReturn(false);

        var results = resolver.caseReasoning(UUID.randomUUID(), 20);

        assertThat(results).isEmpty();
    }

    @Test
    void contributorReasoning_storeNotResolvable_returnsEmptyList() {
        when(memoryStoreInstance.isResolvable()).thenReturn(false);

        var results = resolver.contributorReasoning("alice", 20);

        assertThat(results).isEmpty();
    }

    @Test
    void reasoningTrace_withModuleContext_preservesFields() {
        var trace = new ReasoningTrace("worker1", "code-analysis", "APPROVED",
                "All looks good", Instant.now(), false, null, null);

        var enriched = trace.withModuleContext("app", "casehubio/devtown");

        assertThat(enriched.workerName()).isEqualTo("worker1");
        assertThat(enriched.capability()).isEqualTo("code-analysis");
        assertThat(enriched.outcome()).isEqualTo("APPROVED");
        assertThat(enriched.reasoning()).isEqualTo("All looks good");
        assertThat(enriched.module()).isEqualTo("app");
        assertThat(enriched.repo()).isEqualTo("casehubio/devtown");
    }
}
