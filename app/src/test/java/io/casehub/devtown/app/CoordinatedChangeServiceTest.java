package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.devtown.domain.CoordinatedChangeRequest;
import io.casehub.devtown.domain.RepoChangeEntry;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.PrReviewOutcome;
import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class CoordinatedChangeServiceTest {

    CoordinatedChangeService service;
    CoordinatedChangeCaseHub caseHub;
    PrReviewApplicationService reviewService;
    CoordinatedChangeTracker tracker;
    PrReviewCaseTracker prTracker;
    CaseHubRuntime runtime;

    UUID parentCaseId = UUID.randomUUID();
    UUID reviewCaseIdA = UUID.randomUUID();
    UUID reviewCaseIdB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        caseHub = mock(CoordinatedChangeCaseHub.class);
        reviewService = mock(PrReviewApplicationService.class);
        tracker = new CoordinatedChangeTracker();
        prTracker = new PrReviewCaseTracker();
        runtime = mock(CaseHubRuntime.class);

        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(parentCaseId));
        when(runtime.signal(any(UUID.class), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service = new CoordinatedChangeService();
        service.caseHub = caseHub;
        service.reviewService = reviewService;
        service.tracker = tracker;
        service.prReviewCaseTracker = prTracker;
        service.caseHubRuntime = runtime;
    }

    @Test
    void start_createsParentAndReviewCases() {
        when(reviewService.startReview(any(PrPayload.class), any()))
            .thenReturn(new PrReviewOutcome("case-opened", List.of(), reviewCaseIdA))
            .thenReturn(new PrReviewOutcome("case-opened", List.of(), reviewCaseIdB));

        var request = new CoordinatedChangeRequest(List.of(
            new RepoChangeEntry("casehubio", "engine", 42, "abc", "main", "alice", List.of(), 10),
            new RepoChangeEntry("casehubio", "platform", 99, "def", "main", "bob", List.of(), 20)
        ));

        var outcome = service.start(request);
        assertThat(outcome.parentCaseId()).isEqualTo(parentCaseId);
        assertThat(outcome.reviewCaseIds()).hasSize(2);
        assertThat(outcome.reviewCaseIds().get("casehubio/engine")).isEqualTo(reviewCaseIdA);
        assertThat(outcome.reviewCaseIds().get("casehubio/platform")).isEqualTo(reviewCaseIdB);

        verify(reviewService, times(2)).startReview(any(PrPayload.class), eq(Map.of("coordinatedChange", true)));
        verify(runtime).signal(eq(parentCaseId), eq("reviewCases"), any());
    }

    @Test
    void start_rejectsWhenActiveReviewExists() {
        prTracker.register(UUID.randomUUID(), "t1",
            new PrPayload("casehubio/engine", 42, "abc", "main", 10, "alice", List.of()));

        var request = new CoordinatedChangeRequest(List.of(
            new RepoChangeEntry("casehubio", "engine", 42, "abc", "main", "alice", List.of(), 10)
        ));

        assertThatThrownBy(() -> service.start(request))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void start_cleansUpOnPartialFailure() {
        when(reviewService.startReview(any(PrPayload.class), any()))
            .thenReturn(new PrReviewOutcome("case-opened", List.of(), reviewCaseIdA))
            .thenThrow(new RuntimeException("API error"));

        var request = new CoordinatedChangeRequest(List.of(
            new RepoChangeEntry("casehubio", "engine", 42, "abc", "main", "alice", List.of(), 10),
            new RepoChangeEntry("casehubio", "platform", 99, "def", "main", "bob", List.of(), 20)
        ));

        assertThatThrownBy(() -> service.start(request))
            .isInstanceOf(RuntimeException.class);

        verify(runtime).cancelCase(reviewCaseIdA);
        verify(runtime).cancelCase(parentCaseId);
    }
}
