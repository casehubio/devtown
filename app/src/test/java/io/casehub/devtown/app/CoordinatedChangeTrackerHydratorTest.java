package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.internal.model.CaseMetaModel;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class CoordinatedChangeTrackerHydratorTest {

    CoordinatedChangeTracker         tracker;
    CaseInstanceRepository           repository;
    CurrentPrincipal                 principal;
    CoordinatedChangeTrackerHydrator hydrator;

    @BeforeEach
    void setUp() {
        tracker    = new CoordinatedChangeTracker();
        repository = mock(CaseInstanceRepository.class);
        principal  = mock(CurrentPrincipal.class);
        when(principal.tenancyId()).thenReturn("test-tenant");
        hydrator                        = new CoordinatedChangeTrackerHydrator();
        hydrator.tracker                = tracker;
        hydrator.caseInstanceRepository = repository;
        hydrator.principal              = principal;
    }

    @Test
    void hydrate_rebuildsTrackerFromActiveCases() {
        UUID parentId = UUID.randomUUID();
        UUID reviewA  = UUID.randomUUID();
        UUID reviewB  = UUID.randomUUID();

        var instance = mockCaseInstance(parentId, CaseStatus.RUNNING,
                                        Map.of("reviewCases", Map.of(
                                                "casehubio/engine", reviewA.toString(),
                                                "casehubio/platform", reviewB.toString())));

        when(repository.findByNamespaceAndName("devtown", "coordinated-change", "test-tenant"))
                .thenReturn(List.of(instance));

        hydrator.hydrate();

        assertThat(tracker.findByReviewCaseId(reviewA)).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewA).repo()).isEqualTo("casehubio/engine");
        assertThat(tracker.findByReviewCaseId(reviewB)).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewB).repo()).isEqualTo("casehubio/platform");
        assertThat(tracker.findReviewCaseIds(parentId)).containsExactlyInAnyOrder(reviewA, reviewB);
    }

    @Test
    void hydrate_skipsTerminalCases() {
        UUID parentId = UUID.randomUUID();
        var instance = mockCaseInstance(parentId, CaseStatus.COMPLETED,
                                        Map.of("reviewCases", Map.of("casehubio/engine", UUID.randomUUID().toString())));

        when(repository.findByNamespaceAndName("devtown", "coordinated-change", "test-tenant"))
                .thenReturn(List.of(instance));

        hydrator.hydrate();

        assertThat(tracker.findReviewCaseIds(parentId)).isEmpty();
    }

    @Test
    void hydrate_noActiveCases_trackerStaysEmpty() {
        when(repository.findByNamespaceAndName("devtown", "coordinated-change", "test-tenant"))
                .thenReturn(List.of());

        hydrator.hydrate();
    }

    @Test
    void hydrate_marksAlreadyCompletedRepos() {
        UUID parentId = UUID.randomUUID();
        UUID reviewA  = UUID.randomUUID();
        UUID reviewB  = UUID.randomUUID();

        var instance = mockCaseInstance(parentId, CaseStatus.RUNNING,
                                        Map.of(
                                                "reviewCases", Map.of(
                                                        "casehubio/engine", reviewA.toString(),
                                                        "casehubio/platform", reviewB.toString()),
                                                "completedReviews", Map.of(
                                                        "casehubio/engine", Map.of("status", "completed"))));

        when(repository.findByNamespaceAndName("devtown", "coordinated-change", "test-tenant"))
                .thenReturn(List.of(instance));

        hydrator.hydrate();

        assertThat(tracker.tryTransitionToAllCompleted(parentId)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private CaseInstance mockCaseInstance(UUID caseId, CaseStatus status,
                                          Map<String, Object> contextData) {
        var instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);
        when(instance.getState()).thenReturn(status);
        var metaModel = mock(CaseMetaModel.class);
        when(metaModel.getNamespace()).thenReturn("devtown");
        when(metaModel.getName()).thenReturn("coordinated-change");
        when(instance.getCaseMetaModel()).thenReturn(metaModel);
        var context = mock(CaseContext.class);
        when(context.get(anyString())).thenAnswer(inv -> contextData.get(inv.getArgument(0)));
        when(instance.getCaseContext()).thenReturn(context);
        return instance;
    }
}
