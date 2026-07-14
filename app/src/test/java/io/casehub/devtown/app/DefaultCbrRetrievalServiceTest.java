package io.casehub.devtown.app;

import io.casehub.devtown.domain.cbr.PrFeatureVector;
import io.casehub.devtown.domain.memory.DevtownMemoryDomain;
import io.casehub.devtown.domain.memory.DevtownMemoryKeys;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultCbrRetrievalServiceTest {

    private CaseMemoryStore store;
    private DefaultCbrRetrievalService service;

    @BeforeEach
    void setUp() {
        store = mock(CaseMemoryStore.class);
        var prefs = mock(PreferenceProvider.class);
        var preferences = mock(Preferences.class);
        when(prefs.resolve(any())).thenReturn(preferences);
        when(preferences.getOrDefault(any())).thenAnswer(inv -> {
            var key = inv.getArgument(0, io.casehub.platform.api.preferences.PreferenceKey.class);
            return key.defaultValue();
        });
        service = new DefaultCbrRetrievalService(store, prefs, new CbrWeightOverrideStore());
    }

    @Test
    void returnsPrecedentsRankedBySimilarity() {
        var query = PrFeatureVector.from("repo", 99, "alice", 100,
            List.of("core/src/main/java/Foo.java", "core/src/main/java/Bar.java"));

        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 1, "bob", 100,
            List.of("core/src/main/java/Foo.java", "core/src/main/java/Bar.java"));
        var v2 = PrFeatureVector.from("repo", 2, "carol", 100,
            List.of("core/src/main/java/Foo.java", "api/src/main/java/Qux.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo", "bob", v1),
            buildCaseVectorMemory(id2, "repo", "carol", v2)));

        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
            buildOutcomeMemory(id1, "code-analysis", "COMPLETED", "approved")));

        var results = service.findSimilar(query, "repo", "tenant-1");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).caseId()).isEqualTo(id1);
        assertThat(results.get(0).similarity().score()).isGreaterThan(results.size() > 1 ? results.get(1).similarity().score() : 0.0);
    }

    @Test
    void filtersByRepo() {
        var query = PrFeatureVector.from("repo-a", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo-b", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo-b", "bob", v1)));

        var results = service.findSimilar(query, "repo-a", "tenant-1");
        assertThat(results).isEmpty();
    }

    @Test
    void excludesCasesWithNoOutcomeFacts() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of());

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).isEmpty();
    }

    @Test
    void aggregateOutcome_allApproved() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
            buildOutcomeMemory(id1, "code-analysis", "COMPLETED", "approved"),
            buildOutcomeMemory(id1, "security-review", "COMPLETED", "approved")));

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).outcome()).isEqualTo("approved");
    }

    @Test
    void aggregateOutcome_anyFailed() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
            buildOutcomeMemory(id1, "code-analysis", "COMPLETED", "approved"),
            buildOutcomeMemory(id1, "security-review", "FAILED", "timeout")));

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).outcome()).isEqualTo("failed");
    }

    @Test
    void aggregateOutcome_findingsPresentIsFlagged() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
                                         List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
                                      List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
                buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
                buildOutcomeMemory(id1, "code-analysis", "COMPLETED", "approved"),
                buildOutcomeMemory(id1, "security-review", "COMPLETED", "FINDINGS_PRESENT")));

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).outcome()).isEqualTo("flagged");
    }

    @Test
    void enrichOutcomesIncludesDetail() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
                                         List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
                                      List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
                buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
                buildOutcomeMemory(id1, "security-review", "COMPLETED", "FINDINGS_PRESENT")));

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).hasSize(1);
        var securityOutcome = results.get(0).capabilityOutcomes().get("security-review");
        assertThat(securityOutcome.outcome()).isEqualTo("COMPLETED");
        assertThat(securityOutcome.detail()).isEqualTo("FINDINGS_PRESENT");
        assertThat(securityOutcome.hadFindings()).isTrue();
    }


    @Test
    void failOpenReturnsEmptyOnScanException() {
        when(store.scan(any())).thenThrow(new RuntimeException("scan failed"));

        var results = service.findSimilar(
            PrFeatureVector.from("repo", 1, "alice", 100, List.of()), "repo", "t");
        assertThat(results).isEmpty();
    }

    @Test
    void breakdownIncludedInPrecedent() {
        var query = PrFeatureVector.from("repo", 1, "alice", 100,
            List.of("core/src/main/java/Foo.java"));

        UUID id1 = UUID.randomUUID();
        var v1 = PrFeatureVector.from("repo", 2, "bob", 100,
            List.of("core/src/main/java/Foo.java"));

        when(store.scan(any(MemoryScanRequest.class))).thenReturn(List.of(
            buildCaseVectorMemory(id1, "repo", "bob", v1)));
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(
            buildOutcomeMemory(id1, "code-analysis", "COMPLETED", "approved")));

        var results = service.findSimilar(query, "repo", "tenant-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).similarity().breakdown()).containsKeys(
            "file-paths", "modules", "languages", "change-size", "contributor");
    }

    private Memory buildCaseVectorMemory(UUID caseId, String repo, String contributor, PrFeatureVector v) {
        var attrs = new HashMap<>(v.toAttributes());
        attrs.put(DevtownMemoryKeys.ENTITY_TYPE, "case-vector");
        attrs.put(DevtownMemoryKeys.PR_REPO, repo);
        return new Memory(
            UUID.randomUUID().toString(),
            DevtownMemoryDomain.CASE_VECTOR_PREFIX + repo + ":" + caseId,
            DevtownMemoryDomain.SOFTWARE_REVIEW,
            "tenant-1", caseId.toString(),
            "PR in " + repo, attrs, Instant.now());
    }

    private Memory buildOutcomeMemory(UUID caseId, String capability, String outcome, String detail) {
        var attrs = new HashMap<String, String>();
        attrs.put(MemoryAttributeKeys.OUTCOME, outcome);
        attrs.put(DevtownMemoryKeys.CAPABILITY, capability);
        attrs.put(DevtownMemoryKeys.OUTCOME_DETAIL, detail);
        attrs.put(DevtownMemoryKeys.ENTITY_TYPE, "contributor");
        return new Memory(
            UUID.randomUUID().toString(),
            "contributor:someone", DevtownMemoryDomain.SOFTWARE_REVIEW,
            "tenant-1", caseId.toString(),
            "Review outcome", attrs, Instant.now());
    }
}
