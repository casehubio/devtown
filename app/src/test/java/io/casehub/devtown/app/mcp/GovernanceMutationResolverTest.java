package io.casehub.devtown.app.mcp;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.devtown.app.MergeQueueService;
import io.casehub.devtown.app.PrReviewCaseHub;
import io.casehub.devtown.app.ledger.IncidentFeedbackService;
import io.casehub.devtown.domain.queue.PriorityLane;
import io.casehub.devtown.review.PrPayload;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceMutationResolverTest {

    @Mock CaseHubRuntime caseHubRuntime;
    @Mock PrReviewCaseTracker tracker;
    @Mock PrReviewCaseHub caseHub;
    @Mock CurrentPrincipal principal;
    @Mock IncidentFeedbackService incidentFeedbackService;
    @Mock MergeQueueService mergeQueueService;
    @Mock io.casehub.devtown.review.PrReviewApplicationService reviewService;

    @InjectMocks GovernanceMutationResolver resolver;

    private UUID testCaseId;
    private PrPayload testPayload;
    private CaseInfo testCaseInfo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(principal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
        testCaseId = UUID.randomUUID();
        testPayload = new PrPayload("casehubio/devtown", 42, "abc123", "main", 250, "alice", 0L,
                List.of("src/Main.java", "src/Test.java"));
        Instant now = Instant.now();
        testCaseInfo = new CaseInfo(
                testCaseId, TenancyConstants.DEFAULT_TENANT_ID, testPayload, now, now, CaseTrackingStatus.RUNNING);
    }

    @Test
    void retryReviewer_unknownCase_throws() {
        when(tracker.getCase(testCaseId)).thenReturn(null);

        assertThatThrownBy(() -> resolver.retryReviewer(testCaseId, "code-analysis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Case not found");
    }

    @Test
    void retryReviewer_unknownCapability_throws() {
        when(tracker.getCase(testCaseId)).thenReturn(testCaseInfo);

        assertThatThrownBy(() -> resolver.retryReviewer(testCaseId, "invalid-capability"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown capability");
    }

    @Test
    void retryReviewer_validInputs_signalsCase() {
        when(tracker.getCase(testCaseId)).thenReturn(testCaseInfo);

        GovernanceMutationResolver.RetryResult result = resolver.retryReviewer(testCaseId, "code-analysis");

        assertThat(result.caseId()).isEqualTo(testCaseId);
        assertThat(result.capability()).isEqualTo("code-analysis");
        assertThat(result.status()).isEqualTo("RETRY_SIGNALED");
        verify(caseHubRuntime).signal(testCaseId, "codeAnalysis", null);
    }

    @Test
    void rerouteReview_unknownCase_throws() {
        when(tracker.getCase(testCaseId)).thenReturn(null);

        assertThatThrownBy(() -> resolver.rerouteReview(testCaseId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Case not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rerouteReview_validCase_cancelsAndStartsNew() {
        UUID newCaseId = UUID.randomUUID();
        when(tracker.getCase(testCaseId)).thenReturn(testCaseInfo);
        when(caseHub.startCase(any(Map.class))).thenReturn(newCaseId);

        GovernanceMutationResolver.RerouteResult result = resolver.rerouteReview(testCaseId);

        assertThat(result.oldCaseId()).isEqualTo(testCaseId);
        assertThat(result.newCaseId()).isEqualTo(newCaseId);
        verify(caseHubRuntime).cancelCase(testCaseId);
        verify(caseHub).startCase(any(Map.class));
        verify(tracker).register(eq(newCaseId), anyString(), eq(testPayload));
    }

    @Test
    void forceCompleteCheck_unknownCase_throws() {
        when(tracker.getCase(testCaseId)).thenReturn(null);

        assertThatThrownBy(() -> resolver.forceCompleteCheck(
                testCaseId, "code-analysis", "APPROVED", "Manual override"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Case not found");
    }

    @Test
    void forceCompleteCheck_validInputs_signalsWithOverride() {
        when(tracker.getCase(testCaseId)).thenReturn(testCaseInfo);

        GovernanceMutationResolver.ForceCompleteResult result = resolver.forceCompleteCheck(
                testCaseId, "code-analysis", "APPROVED", "Emergency override");

        assertThat(result.caseId()).isEqualTo(testCaseId);
        assertThat(result.capability()).isEqualTo("code-analysis");
        assertThat(result.outcome()).isEqualTo("APPROVED");
        assertThat(result.status()).isEqualTo("FORCE_COMPLETED");

        verify(caseHubRuntime).signal(
                eq(testCaseId),
                eq("codeAnalysis"),
                argThat(obj -> obj instanceof Map && ((Map<?, ?>) obj).get("operatorOverride").equals(true)));
    }

    @Test
    void enqueuePr_validInputs_enqueuesSuccessfully() {
        when(mergeQueueService.enqueue(any())).thenReturn(true);

        GovernanceMutationResolver.EnqueueResult result = resolver.enqueuePr(
                "casehubio/devtown", 99, "deadbeef", "alice", 0.75, "HIGH");

        assertThat(result.prNumber()).isEqualTo(99);
        assertThat(result.lane()).isEqualTo("HIGH");
        assertThat(result.status()).isEqualTo("ENQUEUED");
    }

    @Test
    void enqueuePr_duplicate_returnsAlreadyQueued() {
        when(mergeQueueService.enqueue(any())).thenReturn(false);

        GovernanceMutationResolver.EnqueueResult result = resolver.enqueuePr(
                "casehubio/devtown", 99, "deadbeef", "alice", 0.75, "HIGH");

        assertThat(result.status()).isEqualTo("ALREADY_QUEUED");
    }

    @Test
    void enqueuePr_nullPriority_defaultsToNormal() {
        GovernanceMutationResolver.EnqueueResult result = resolver.enqueuePr(
                "casehubio/devtown", 50, "abc123", "bob", 0.5, null);

        assertThat(result.lane()).isEqualTo("NORMAL");
        verify(mergeQueueService).enqueue(argThat(pr -> pr.lane() == PriorityLane.NORMAL));
    }

    @Test
    void enqueuePr_blankRepo_throws() {
        assertThatThrownBy(() -> resolver.enqueuePr("", 1, "sha", "alice", 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repo is required");
    }

    @Test
    void enqueuePr_invalidTrustScore_throws() {
        assertThatThrownBy(() -> resolver.enqueuePr("repo", 1, "sha", "alice", 1.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trustScore");
    }

    @Test
    void dequeuePr_existing_returnsRemoved() {
        when(mergeQueueService.dequeue(42, "casehubio/devtown")).thenReturn(true);

        GovernanceMutationResolver.DequeueResult result = resolver.dequeuePr("casehubio/devtown", 42);

        assertThat(result.prNumber()).isEqualTo(42);
        assertThat(result.removed()).isTrue();
        assertThat(result.status()).isEqualTo("REMOVED");
    }

    @Test
    void dequeuePr_notFound_returnsNotFound() {
        when(mergeQueueService.dequeue(999, "casehubio/devtown")).thenReturn(false);

        GovernanceMutationResolver.DequeueResult result = resolver.dequeuePr("casehubio/devtown", 999);

        assertThat(result.removed()).isFalse();
        assertThat(result.status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void dequeuePr_blankRepo_throws() {
        assertThatThrownBy(() -> resolver.dequeuePr("", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repo is required");
    }

    @Test
    void supersedePr_activeCase_returnsSuperseded() {
        UUID oldCaseId = UUID.randomUUID();
        UUID newCaseId = UUID.randomUUID();
        var outcome = new io.casehub.devtown.review.PrReviewOutcome("case-opened", List.of(), null);
        var result = new io.casehub.devtown.review.SupersedeResult(oldCaseId, newCaseId, outcome);
        when(reviewService.supersedePr(anyString(), anyInt(), any(PrPayload.class))).thenReturn(result);

        var toolResult = resolver.supersedePr(
                "casehubio/devtown", 42, 43, "newsha", "main", 100, "bob", "src/A.java,src/B.java");

        assertThat(toolResult.status()).isEqualTo("superseded");
        assertThat(toolResult.supersededCaseId()).isEqualTo(oldCaseId);
        assertThat(toolResult.replacementCaseId()).isEqualTo(newCaseId);
    }

    @Test
    void supersedePr_noActiveCase_returnsNoActiveCase() {
        when(reviewService.supersedePr(anyString(), anyInt(), any(PrPayload.class)))
                .thenReturn(io.casehub.devtown.review.SupersedeResult.noActiveCase());

        var toolResult = resolver.supersedePr(
                "casehubio/devtown", 42, 43, "newsha", "main", 100, "bob", "src/A.java");

        assertThat(toolResult.status()).isEqualTo("no-active-case");
        assertThat(toolResult.supersededCaseId()).isNull();
        assertThat(toolResult.replacementCaseId()).isNull();
    }

    @Test
    void supersedePr_alreadyTerminal_returnsAlreadyTerminal() {
        UUID oldCaseId = UUID.randomUUID();
        when(reviewService.supersedePr(anyString(), anyInt(), any(PrPayload.class)))
                .thenReturn(io.casehub.devtown.review.SupersedeResult.alreadyTerminal(oldCaseId));

        var toolResult = resolver.supersedePr(
                "casehubio/devtown", 42, 43, "newsha", "main", 100, "bob", "src/A.java");

        assertThat(toolResult.status()).isEqualTo("already-terminal");
        assertThat(toolResult.supersededCaseId()).isEqualTo(oldCaseId);
        assertThat(toolResult.replacementCaseId()).isNull();
    }
}
