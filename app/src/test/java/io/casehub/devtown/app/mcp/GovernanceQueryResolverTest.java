package io.casehub.devtown.app.mcp;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.devtown.app.MergeQueueService;
import io.casehub.devtown.app.governance.GovernanceQueryService;
import io.casehub.devtown.review.PrPayload;
import io.casehub.ledger.runtime.service.LedgerProvExportService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceQueryResolverTest {

    @Mock GovernanceQueryService governanceQuery;
    @Mock LedgerProvExportService provExportService;
    @Mock CurrentPrincipal principal;
    @Mock CaseHubRuntime caseHubRuntime;
    @Mock MergeQueueService mergeQueueService;

    @InjectMocks GovernanceQueryResolver resolver;

    private UUID testCaseId;
    private PrPayload testPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(principal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
        testCaseId = UUID.randomUUID();
        testPayload = new PrPayload("casehubio/devtown", 42, "abc123", "main", 250, "alice", 0L,
                List.of("src/Main.java", "src/Test.java"));
    }

    @Test
    void queueStatus_emptyTracker_returnsZeroCounts() {
        var emptyStatus = new GovernanceQueryService.QueueStatus(0, Map.of(), List.of());
        when(governanceQuery.queueStatus()).thenReturn(emptyStatus);

        GovernanceQueryService.QueueStatus status = resolver.queueStatus();

        assertThat(status.total()).isZero();
        assertThat(status.countsByStatus()).isEmpty();
        assertThat(status.reviews()).isEmpty();
    }

    @Test
    void queueStatus_withRegisteredCases_returnsCorrectCountsAndReviews() {
        Instant now = Instant.now();
        var review1 = new GovernanceQueryService.ActiveReview(
                UUID.randomUUID(), "casehubio/devtown", 42, "alice", 250, "RUNNING", now, now);
        var review2 = new GovernanceQueryService.ActiveReview(
                UUID.randomUUID(), "casehubio/devtown", 42, "alice", 250, "WAITING", now, now);
        Map<String, Integer> counts = Map.of("RUNNING", 1, "WAITING", 1);
        var status = new GovernanceQueryService.QueueStatus(2, counts, List.of(review1, review2));
        when(governanceQuery.queueStatus()).thenReturn(status);

        GovernanceQueryService.QueueStatus result = resolver.queueStatus();

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.countsByStatus()).containsEntry("RUNNING", 1);
        assertThat(result.countsByStatus()).containsEntry("WAITING", 1);
        assertThat(result.reviews()).hasSize(2);
        assertThat(result.reviews().get(0).repo()).isEqualTo("casehubio/devtown");
        assertThat(result.reviews().get(0).prNumber()).isEqualTo(42);
    }

    @Test
    void recentEvents_delegatesToGovernanceQuery() {
        Instant now = Instant.now();
        TrackedEvent event = new TrackedEvent(
                now, testCaseId, "casehubio/devtown", 42, "CaseStarted", "RUNNING", "system");
        when(governanceQuery.recentEvents(50, null)).thenReturn(List.of(event));

        List<TrackedEvent> events = resolver.recentEvents(50, null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("CaseStarted");
        verify(governanceQuery).recentEvents(50, null);
    }

    @Test
    void recentEvents_withLimitAndSince_passesParameters() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        when(governanceQuery.recentEvents(eq(10), any(Instant.class))).thenReturn(List.of());

        resolver.recentEvents(10, since.toString());

        verify(governanceQuery).recentEvents(eq(10), argThat(instant ->
                instant != null && instant.equals(since)));
    }

    @Test
    void systemHealth_assemblesFromMultipleSources() {
        var health = new GovernanceQueryService.SystemHealth(1, 0, Map.of(), 0, 0);
        when(governanceQuery.systemHealth()).thenReturn(health);

        GovernanceQueryService.SystemHealth result = resolver.systemHealth();

        assertThat(result.activeCases()).isEqualTo(1);
        assertThat(result.fleetSize()).isZero();
        assertThat(result.openCommitments()).isZero();
    }

    @Test
    void problems_findsStalledCases() {
        Instant staleTime = Instant.now().minus(90, ChronoUnit.MINUTES);
        var problem = new GovernanceQueryService.Problem(
                "stalled_case", "warning", "Case stalled for 90 minutes", testCaseId, null, staleTime);
        when(governanceQuery.problems(60)).thenReturn(List.of(problem));

        List<GovernanceQueryService.Problem> problems = resolver.problems(60);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).category()).isEqualTo("stalled_case");
        assertThat(problems.get(0).severity()).isEqualTo("warning");
        assertThat(problems.get(0).caseId()).isEqualTo(testCaseId);
    }

    @Test
    void problems_findsExpiredCommitments() {
        Instant expired = Instant.now().minus(10, ChronoUnit.MINUTES);
        var problem = new GovernanceQueryService.Problem(
                "expired_commitment", "error", "Commitment expired 10 minutes ago", null, "reviewer-1", expired);
        when(governanceQuery.problems(60)).thenReturn(List.of(problem));

        List<GovernanceQueryService.Problem> problems = resolver.problems(60);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).category()).isEqualTo("expired_commitment");
        assertThat(problems.get(0).severity()).isEqualTo("error");
        assertThat(problems.get(0).actorId()).isEqualTo("reviewer-1");
    }

    @Test
    void problems_findsFailedWorkers() {
        Instant now = Instant.now();
        var problem = new GovernanceQueryService.Problem(
                "worker_failure", "error", "Worker failed", testCaseId, "reviewer-1", now);
        when(governanceQuery.problems(60)).thenReturn(List.of(problem));

        List<GovernanceQueryService.Problem> problems = resolver.problems(60);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).category()).isEqualTo("worker_failure");
        assertThat(problems.get(0).actorId()).isEqualTo("reviewer-1");
    }

    @Test
    void problems_detectsQueueSlaBreaches() {
        Instant longAgo = Instant.now().minus(180, ChronoUnit.MINUTES);
        var problem = new GovernanceQueryService.Problem(
                "queue_sla_breach", "warning",
                "PR #77 (CRITICAL lane) has waited 180 minutes, exceeding SLA of 60 minutes",
                null, "dave", longAgo);
        when(governanceQuery.problems(60)).thenReturn(List.of(problem));

        List<GovernanceQueryService.Problem> problems = resolver.problems(60);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).category()).isEqualTo("queue_sla_breach");
        assertThat(problems.get(0).description()).contains("PR #77").contains("CRITICAL");
        assertThat(problems.get(0).actorId()).isEqualTo("dave");
    }

    @Test
    void reviewDetail_unknownCase_throwsIllegalArgumentException() {
        when(governanceQuery.reviewDetail(eq(testCaseId), anyString()))
                .thenThrow(new IllegalArgumentException("Case not found: " + testCaseId));

        assertThatThrownBy(() -> resolver.reviewDetail(testCaseId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Case not found");
    }

    @Test
    void reviewDetail_knownCase_returnsDetailWithTimeline() {
        Instant now = Instant.now();
        var timelineEvent = new GovernanceQueryService.EventEntry(
                now, "CASE_STARTED", "system", "Case started");
        var capability = new GovernanceQueryService.CapabilityStatus(
                "code-analysis", "COMPLETED", "APPROVED", now.plusSeconds(10));
        var detail = new GovernanceQueryService.ReviewDetail(
                testCaseId, testPayload, List.of(timelineEvent), List.of(capability));
        when(governanceQuery.reviewDetail(eq(testCaseId), anyString())).thenReturn(detail);

        GovernanceQueryResolver.ReviewDetailResult result = resolver.reviewDetail(testCaseId);

        assertThat(result.caseId()).isEqualTo(testCaseId);
        assertThat(result.pr().repo()).isEqualTo("casehubio/devtown");
        assertThat(result.pr().prNumber()).isEqualTo(42);
        assertThat(result.timeline()).hasSize(1);
        assertThat(result.capabilities()).hasSize(1);
        assertThat(result.capabilities().get(0).name()).isEqualTo("code-analysis");
        assertThat(result.capabilities().get(0).status()).isEqualTo("COMPLETED");}

    @Test
    void reviewerHealth_returnsCommitmentCountAndTrustScores() {
        var health = new GovernanceQueryService.ReviewerHealth(
                "reviewer-1", 1,
                Map.of("code-analysis", 0.75, "security-review", 0.82),
                Map.of("review-thoroughness", 0.80),
                5, List.of());
        when(governanceQuery.reviewerHealth("reviewer-1")).thenReturn(health);

        GovernanceQueryService.ReviewerHealth result = resolver.reviewerHealth("reviewer-1");

        assertThat(result.reviewerId()).isEqualTo("reviewer-1");
        assertThat(result.openCommitments()).isEqualTo(1);
        assertThat(result.trustByCapability()).containsEntry("code-analysis", 0.75);
        assertThat(result.trustByDimension()).containsEntry("review-thoroughness", 0.80);
        assertThat(result.totalDecisions()).isGreaterThan(0);
    }

    @Test
    void mergeQueue_emptyQueue_returnsZeroCounts() {
        var emptyStatus = new GovernanceQueryService.MergeQueueStatus(0, 0, List.of(), List.of());
        when(governanceQuery.mergeQueue()).thenReturn(emptyStatus);

        GovernanceQueryService.MergeQueueStatus status = resolver.mergeQueue();

        assertThat(status.queuedCount()).isZero();
        assertThat(status.activeBatchCount()).isZero();
        assertThat(status.queuedPrs()).isEmpty();
        assertThat(status.activeBatches()).isEmpty();
    }

    @Test
    void mergeQueue_withQueuedPrsAndBatches_returnsCorrectState() {
        Instant enqueued = Instant.now().minus(30, ChronoUnit.MINUTES);
        UUID batchCaseId = UUID.randomUUID();
        var queuedPr = new GovernanceQueryService.QueuedPrEntry(
                101, "casehubio/devtown", "sha1", "alice", 0.85, "HIGH",
                enqueued, 30, java.util.Set.of());
        var batchSummary = new GovernanceQueryService.ActiveBatchEntry(
                batchCaseId, "batch-1", 1, "low");
        var status = new GovernanceQueryService.MergeQueueStatus(
                1, 1, List.of(queuedPr), List.of(batchSummary));
        when(governanceQuery.mergeQueue()).thenReturn(status);

        GovernanceQueryService.MergeQueueStatus result = resolver.mergeQueue();

        assertThat(result.queuedCount()).isEqualTo(1);
        assertThat(result.activeBatchCount()).isEqualTo(1);
        assertThat(result.queuedPrs()).hasSize(1);
        assertThat(result.queuedPrs().get(0).number()).isEqualTo(101);
        assertThat(result.queuedPrs().get(0).priorityLane()).isEqualTo("HIGH");
        assertThat(result.queuedPrs().get(0).waitMinutes()).isGreaterThanOrEqualTo(29);
        assertThat(result.activeBatches()).hasSize(1);
        assertThat(result.activeBatches().get(0).batchId()).isEqualTo("batch-1");
    }

    @Test
    void batchStatus_unknownBatch_throws() {
        UUID unknownId = UUID.randomUUID();
        when(governanceQuery.batchStatus(unknownId))
                .thenThrow(new IllegalArgumentException("No active batch found for caseId: " + unknownId));

        assertThatThrownBy(() -> resolver.batchStatus(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active batch found");
    }

    @Test
    void batchStatus_knownBatch_returnsDetail() {
        UUID batchCaseId = UUID.randomUUID();
        var pr1 = new GovernanceQueryService.BatchPrEntry(
                10, "casehubio/devtown", "sha1", "alice", 0.8, "NORMAL");
        var pr2 = new GovernanceQueryService.BatchPrEntry(
                11, "casehubio/devtown", "sha2", "bob", 0.7, "NORMAL");
        var status = new GovernanceQueryService.BatchStatus(
                "batch-2", batchCaseId, List.of(pr1, pr2), "low", "sequential");
        when(governanceQuery.batchStatus(batchCaseId)).thenReturn(status);

        GovernanceQueryService.BatchStatus result = resolver.batchStatus(batchCaseId);

        assertThat(result.batchId()).isEqualTo("batch-2");
        assertThat(result.caseId()).isEqualTo(batchCaseId);
        assertThat(result.prs()).hasSize(2);
        assertThat(result.prs().get(0).number()).isEqualTo(10);
        assertThat(result.prs().get(1).number()).isEqualTo(11);
    }

    @Test
    void mergeQueueMetrics_emptyQueue_returnsZeros() {
        var emptyMetrics = new GovernanceQueryService.MergeQueueMetrics(
                0, 0, 0L, 0L, 0.0, Map.of(), 0, 0.0, Map.of());
        when(governanceQuery.mergeQueueMetrics()).thenReturn(emptyMetrics);

        GovernanceQueryService.MergeQueueMetrics metrics = resolver.mergeQueueMetrics();

        assertThat(metrics.queueDepth()).isZero();
        assertThat(metrics.activeBatches()).isZero();
        assertThat(metrics.oldestWaitMinutes()).isZero();
        assertThat(metrics.avgWaitMinutes()).isZero();
        assertThat(metrics.avgTrustScore()).isZero();
        assertThat(metrics.countsByLane()).isEmpty();
        assertThat(metrics.throughput24h()).isZero();
        assertThat(metrics.failureRate()).isZero();
        assertThat(metrics.batchSizeDistribution()).isEmpty();
    }

    @Test
    void mergeQueueMetrics_withPrs_computesCorrectly() {
        var metrics = new GovernanceQueryService.MergeQueueMetrics(
                2, 0, 60L, 35L, 0.7,
                Map.of("NORMAL", 1, "HIGH", 1),
                1, 0.25, Map.of(2, 1));
        when(governanceQuery.mergeQueueMetrics()).thenReturn(metrics);

        GovernanceQueryService.MergeQueueMetrics result = resolver.mergeQueueMetrics();

        assertThat(result.queueDepth()).isEqualTo(2);
        assertThat(result.oldestWaitMinutes()).isGreaterThanOrEqualTo(59);
        assertThat(result.avgWaitMinutes()).isGreaterThanOrEqualTo(34);
        assertThat(result.avgTrustScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.countsByLane()).containsEntry("NORMAL", 1).containsEntry("HIGH", 1);
        assertThat(result.throughput24h()).isEqualTo(1);
        assertThat(result.failureRate()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.batchSizeDistribution()).containsEntry(2, 1);
    }

    @Test
    void exportProv_delegatesToProvExportService() {
        String provJson = "{\"@context\": \"http://www.w3.org/ns/prov#\"}";
        when(provExportService.exportSubject(testCaseId, TenancyConstants.DEFAULT_TENANT_ID))
                .thenReturn(provJson);

        String result = resolver.exportProv(testCaseId);

        assertThat(result).isEqualTo(provJson);
        verify(provExportService).exportSubject(testCaseId, TenancyConstants.DEFAULT_TENANT_ID);
    }
}
