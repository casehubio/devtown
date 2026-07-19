package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class CoordinatedChangeObserverTest {

    CoordinatedChangeObserver observer;
    CoordinatedChangeTracker tracker;
    CaseHubRuntime runtime;
    UUID parentId = UUID.randomUUID();
    UUID reviewA = UUID.randomUUID();
    UUID reviewB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker = new CoordinatedChangeTracker();
        runtime = mock(CaseHubRuntime.class);
        when(runtime.signal(any(UUID.class), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(runtime.signal(any(UUID.class), anyString(), any(), any(Map.class))).thenReturn(CompletableFuture.completedFuture(null));
        observer = new CoordinatedChangeObserver(tracker, runtime);
        tracker.register(parentId, "casehubio/engine", reviewA);
        tracker.register(parentId, "casehubio/platform", reviewB);
    }

    @Test
    void reviewCompletion_signalsParentContext() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED", "pr-approved", "success"));
        verify(runtime).signal(eq(parentId), eq("completedReviews.casehubio/engine"), any(Map.class), any(Map.class));
    }

    @Test
    void allReviewsComplete_signalsAllReviewsCompleted() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED", "pr-approved", "success"));
        observer.onCaseLifecycle(lifecycleEvent(reviewB, "COMPLETED", "pr-approved", "success"));
        verify(runtime).signal(eq(parentId), eq("allReviewsCompleted"), eq(true), any(Map.class));
    }

    @Test
    void reviewFault_signalsReviewFaulted() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "FAULTED"));
        verify(runtime).signal(eq(parentId), eq("reviewFaulted"), any(Map.class), any(Map.class));
    }

    @Test
    void failureGoalCompleted_signalsReviewFaulted() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED", "review-abandoned", "failure"));
        verify(runtime).signal(eq(parentId), eq("reviewFaulted"), any(Map.class), any(Map.class));
        verify(runtime, never()).signal(eq(parentId), eq("completedReviews.casehubio/engine"), any(), any());
    }

    @Test
    void untrackedCase_ignored() {
        observer.onCaseLifecycle(lifecycleEvent(UUID.randomUUID(), "COMPLETED"));
        verifyNoInteractions(runtime);
    }

    @Test
    void parentTerminal_skipsSignaling() {
        tracker.markParentTerminal(parentId);
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED"));
        verifyNoInteractions(runtime);
    }

    @Test
    void parentTerminal_cancelsRemainingReviews() {
        var parentEvent = new CaseLifecycleEvent(parentId, null, null, null, "FAULTED",
            null, null, null, "coordinated-change", "devtown", null, null, null);
        observer.onParentTerminal(parentEvent);

        verify(runtime).cancelCase(reviewA);
        verify(runtime).cancelCase(reviewB);
    }

    @Test
    void parentTerminalForUnknownCase_ignored() {
        var unknownParent = new CaseLifecycleEvent(UUID.randomUUID(), null, null, null, "FAULTED",
            null, null, null, "coordinated-change", "devtown", null, null, null);
        observer.onParentTerminal(unknownParent);
        verify(runtime, never()).cancelCase(any());
    }

    @Test
    void nonTerminalStatus_ignored() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "RUNNING"));
        verifyNoInteractions(runtime);
    }

    @SuppressWarnings("unchecked")
    @Test
    void signalCarriesProvenanceMetadata() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED", "pr-approved", "success"));

        var captor = ArgumentCaptor.forClass(Map.class);
        verify(runtime).signal(eq(parentId), eq("completedReviews.casehubio/engine"),
            any(), captor.capture());
        Map<String, Object> provenance = captor.getValue();
        assertThat(provenance).containsEntry("causedByCaseId", reviewA.toString());
    }

    private CaseLifecycleEvent lifecycleEvent(UUID caseId, String status) {
        return new CaseLifecycleEvent(caseId, null, null, null, status,
            null, null, null, null, null, null, null, null);
    }

    private CaseLifecycleEvent lifecycleEvent(UUID caseId, String status,
        String goalName, String goalKind) {
        return new CaseLifecycleEvent(caseId, null, null, null, status,
            null, null, null, null, null, null, goalName, goalKind);
    }
}
