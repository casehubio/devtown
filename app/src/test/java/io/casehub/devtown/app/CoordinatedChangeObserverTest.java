package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        observer = new CoordinatedChangeObserver(tracker, runtime);
        tracker.register(parentId, "casehubio/engine", reviewA);
        tracker.register(parentId, "casehubio/platform", reviewB);
    }

    @Test
    void reviewCompletion_signalsParentContext() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED"));
        verify(runtime).signal(eq(parentId), eq("completedReviews.casehubio/engine"), any(Map.class));
    }

    @Test
    void allReviewsComplete_signalsAllReviewsCompleted() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "COMPLETED"));
        observer.onCaseLifecycle(lifecycleEvent(reviewB, "COMPLETED"));
        verify(runtime).signal(eq(parentId), eq("allReviewsCompleted"), eq(true));
    }

    @Test
    void reviewFault_signalsReviewFaulted() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "FAULTED"));
        verify(runtime).signal(eq(parentId), eq("reviewFaulted"), any(Map.class));
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
            null, null, null, "coordinated-change", "devtown", null);
        observer.onParentTerminal(parentEvent);

        verify(runtime).cancelCase(reviewA);
        verify(runtime).cancelCase(reviewB);
    }

    @Test
    void parentTerminalForUnknownCase_ignored() {
        var unknownParent = new CaseLifecycleEvent(UUID.randomUUID(), null, null, null, "FAULTED",
            null, null, null, "coordinated-change", "devtown", null);
        observer.onParentTerminal(unknownParent);
        verify(runtime, never()).cancelCase(any());
    }

    @Test
    void nonTerminalStatus_ignored() {
        observer.onCaseLifecycle(lifecycleEvent(reviewA, "RUNNING"));
        verifyNoInteractions(runtime);
    }

    private CaseLifecycleEvent lifecycleEvent(UUID caseId, String status) {
        return new CaseLifecycleEvent(caseId, null, null, null, status,
            null, null, null, null, null, null);
    }
}
