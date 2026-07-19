package io.casehub.devtown.app;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.devtown.domain.CoordinatedChangeRequest;
import io.casehub.devtown.domain.MergeClient;
import io.casehub.devtown.domain.MergeOutcome;
import io.casehub.devtown.domain.RepoChangeEntry;
import io.casehub.devtown.domain.RevertClient;
import io.casehub.devtown.domain.RevertOutcome;
import io.casehub.devtown.review.CoordinatedChangeOutcome;
import io.casehub.devtown.review.CoordinatedChangePort;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class CrossRepoCoordinatedMergeTest {

    private static final Set<CaseStatus> TERMINAL = Set.of(
        CaseStatus.COMPLETED, CaseStatus.FAULTED, CaseStatus.CANCELLED);

    @Inject CoordinatedChangePort coordinatedChangeService;
    @Inject CoordinatedChangeTracker tracker;
    @Inject PrReviewCaseHub prReviewCaseHub;
    @Inject CoordinatedChangeCaseHub coordinatedChangeCaseHub;
    @Inject CrossTenantCaseInstanceRepository caseInstanceRepository;
    @Inject CaseHubRuntime caseHubRuntime;
    @Inject WorkItemQueries workItemQueries;
    @Inject TestMergeClient testMergeClient;
    @Inject TestRevertClient testRevertClient;

    @BeforeEach
    void setUp() {
        testMergeClient.reset();
        testRevertClient.reset();
    }

    // ── @Alternative stubs ───────────────────────────────────────────

    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class TestMergeClient implements MergeClient {
        private final Queue<MergeOutcome> outcomes = new LinkedList<>();
        private final List<MergeCall> calls = Collections.synchronizedList(new ArrayList<>());

        public record MergeCall(String owner, String repo, int prNumber, String headSha) {}

        @Override
        public MergeOutcome merge(String owner, String repo, int prNumber, String headSha) {
            calls.add(new MergeCall(owner, repo, prNumber, headSha));
            MergeOutcome outcome = outcomes.poll();
            if (outcome == null) throw new IllegalStateException(
                "TestMergeClient: no outcome programmed for " + owner + "/" + repo + "#" + prNumber);
            return outcome;
        }

        public void enqueue(MergeOutcome... results) {
            Collections.addAll(outcomes, results);
        }

        public List<MergeCall> calls() { return List.copyOf(calls); }

        public void reset() {
            outcomes.clear();
            calls.clear();
        }
    }

    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class TestRevertClient implements RevertClient {
        private final Queue<RevertOutcome> outcomes = new LinkedList<>();
        private final List<RevertCall> calls = Collections.synchronizedList(new ArrayList<>());

        public record RevertCall(String owner, String repo, String targetBranch,
                                 String mergeSha, String commitMessage) {}

        @Override
        public RevertOutcome revert(String owner, String repo, String targetBranch,
                                    String mergeSha, String commitMessage) {
            calls.add(new RevertCall(owner, repo, targetBranch, mergeSha, commitMessage));
            RevertOutcome outcome = outcomes.poll();
            if (outcome == null) throw new IllegalStateException(
                "TestRevertClient: no outcome programmed for " + owner + "/" + repo);
            return outcome;
        }

        public void enqueue(RevertOutcome... results) {
            Collections.addAll(outcomes, results);
        }

        public List<RevertCall> calls() { return List.copyOf(calls); }

        public void reset() {
            outcomes.clear();
            calls.clear();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private CoordinatedChangeRequest buildRequest(RepoChangeEntry... repos) {
        return new CoordinatedChangeRequest(List.of(repos));
    }

    private void preSeedCapabilityKeys(UUID reviewCaseId) {
        caseHubRuntime.signal(reviewCaseId, "codeAnalysis", Map.of("outcome", "PENDING"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "styleCheck", Map.of("outcome", "PENDING"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "testCoverage", Map.of("outcome", "PENDING"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "performanceAnalysis", Map.of("outcome", "PENDING"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "ci", Map.of("status", "PENDING"))
            .toCompletableFuture().join();
    }

    private void driveReviewToCompletion(UUID reviewCaseId) {
        caseHubRuntime.signal(reviewCaseId, "codeAnalysis",
            Map.of("complete", true, "securitySensitive", false, "architectureCrossing", false))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "styleCheck", Map.of("outcome", "APPROVED"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "testCoverage", Map.of("outcome", "APPROVED"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "performanceAnalysis", Map.of("outcome", "APPROVED"))
            .toCompletableFuture().join();
        caseHubRuntime.signal(reviewCaseId, "ci", Map.of("status", "passing"))
            .toCompletableFuture().join();
    }

    private void driveReviewToFault(UUID reviewCaseId) {
        caseHubRuntime.cancelCase(reviewCaseId);
    }

    private void awaitCaseStatus(UUID caseId, CaseStatus expected) {
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var instance = caseInstanceRepository.findByUuid(caseId);
            assertThat(instance).as("Case %s should exist", caseId).isNotNull();
            assertThat(instance.getState()).as("Case %s status", caseId).isEqualTo(expected);
        });
    }

    private void awaitCaseTerminal(UUID caseId) {
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var instance = caseInstanceRepository.findByUuid(caseId);
            assertThat(instance).isNotNull();
            assertThat(TERMINAL).as("Case %s should be terminal but is %s", caseId, instance.getState())
                .contains(instance.getState());
        });
    }

    private Object contextValue(UUID caseId, String path) {
        var instance = caseInstanceRepository.findByUuid(caseId);
        return instance.getCaseContext().getPath(path);
    }

    // ── Scenarios ────────────────────────────────────────────────────

    @Test
    void happyPath_allReviewsComplete_mergeSucceeds_parentCompleted() {
        testMergeClient.enqueue(
            new MergeOutcome.Success("sha-engine"),
            new MergeOutcome.Success("sha-platform"));

        var request = buildRequest(
            new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 10),
            new RepoChangeEntry("casehubio", "platform", 99, "def456", "main", "bob", List.of("src/Config.java"), 10));

        // ── Phase 1: Initiation ──────────────────────────────────────
        CoordinatedChangeOutcome outcome = coordinatedChangeService.start(request);
        UUID parentCaseId = outcome.parentCaseId();
        UUID reviewA = outcome.reviewCaseIds().get("casehubio/engine");
        UUID reviewB = outcome.reviewCaseIds().get("casehubio/platform");

        assertThat(parentCaseId).isNotNull();
        assertThat(reviewA).isNotNull();
        assertThat(reviewB).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewA)).isNotNull();
        assertThat(tracker.findByReviewCaseId(reviewA).parentCaseId()).isEqualTo(parentCaseId);
        assertThat(tracker.findByReviewCaseId(reviewB).parentCaseId()).isEqualTo(parentCaseId);

        var parentInstance = caseInstanceRepository.findByUuid(parentCaseId);
        assertThat(parentInstance).isNotNull();
        assertThat(parentInstance.getCaseContext().getPath("reviewCases")).isNotNull();

        // ── Phase 2: Pre-seed + drive review cases to completion ─────
        preSeedCapabilityKeys(reviewA);
        preSeedCapabilityKeys(reviewB);
        driveReviewToCompletion(reviewA);
        driveReviewToCompletion(reviewB);

        awaitCaseStatus(reviewA, CaseStatus.COMPLETED);
        awaitCaseStatus(reviewB, CaseStatus.COMPLETED);

        // ── Phase 3: Coordination bridge ─────────────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            assertThat(contextValue(parentCaseId, "completedReviews.casehubio/engine.status"))
                .isEqualTo("completed");
            assertThat(contextValue(parentCaseId, "completedReviews.casehubio/platform.status"))
                .isEqualTo("completed");
            assertThat(contextValue(parentCaseId, "allReviewsCompleted"))
                .isEqualTo(true);
        });

        // ── Phase 4: Worker execution ────────────────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
            assertThat(contextValue(parentCaseId, "mergeResults")).isNotNull());

        assertThat(testMergeClient.calls()).hasSize(2);
        assertThat(testMergeClient.calls().get(0).repo()).isEqualTo("engine");
        assertThat(testMergeClient.calls().get(1).repo()).isEqualTo("platform");

        // ── Phase 5: Terminal state ──────────────────────────────────
        awaitCaseStatus(parentCaseId, CaseStatus.COMPLETED);

        var reviewAInstance = caseInstanceRepository.findByUuid(reviewA);
        assertThat(reviewAInstance.getCaseContext().getPath("coordinatedChange")).isEqualTo(true);

        // ── Phase 6: EventLog ────────────────────────────────────────
        var allEvents = caseHubRuntime.eventLog(parentCaseId).toCompletableFuture().join();
        assertThat(allEvents).as("Parent case should have EventLog entries").isNotEmpty();

        var signals = allEvents.stream()
            .filter(e -> e.eventType() == CaseHubEventType.SIGNAL_RECEIVED).toList();
        assertThat(signals).as("SIGNAL_RECEIVED events").isNotEmpty();

        var provenanceSignals = signals.stream()
            .filter(e -> e.metadata() != null && e.metadata().has("causedByCaseId"))
            .toList();
        assertThat(provenanceSignals).as("Signals should carry cross-case provenance").isNotEmpty();

        var workerExecs = allEvents.stream()
            .filter(e -> e.eventType() == CaseHubEventType.WORKER_EXECUTION_COMPLETED).toList();
        assertThat(workerExecs).as("WORKER_EXECUTION_COMPLETED events").isNotEmpty();
    }

    @Test
    void faultPath_reviewFaults_parentTerminates_remainingStaysCompleted() {
        var request = buildRequest(
                new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 10),
                new RepoChangeEntry("casehubio", "platform", 99, "def456", "main", "bob", List.of("src/Config.java"), 10));

        // ── Phase 1: Initiation ──────────────────────────────────────
        CoordinatedChangeOutcome outcome      = coordinatedChangeService.start(request);
        UUID                     parentCaseId = outcome.parentCaseId();
        UUID                     reviewA      = outcome.reviewCaseIds().get("casehubio/engine");
        UUID                     reviewB      = outcome.reviewCaseIds().get("casehubio/platform");

        // ── Phase 2: Divergent lifecycle ─────────────────────────────
        preSeedCapabilityKeys(reviewA);
        preSeedCapabilityKeys(reviewB);
        driveReviewToCompletion(reviewA);
        awaitCaseStatus(reviewA, CaseStatus.COMPLETED);

        driveReviewToFault(reviewB);

        // ── Phase 3: Fault propagation ───────────────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            assertThat(contextValue(parentCaseId, "reviewFaulted.repo"))
                    .isEqualTo("casehubio/platform");
            assertThat(contextValue(parentCaseId, "reviewFaulted.reason"))
                    .isEqualTo("CANCELLED");
        });

        // ── Phase 4: Terminal + cancel propagation ───────────────────
        awaitCaseTerminal(parentCaseId);

        var reviewAInstance = caseInstanceRepository.findByUuid(reviewA);
        assertThat(reviewAInstance.getState()).isEqualTo(CaseStatus.COMPLETED);

        assertThat(testMergeClient.calls()).isEmpty();

        // ── Phase 5: EventLog ────────────────────────────────────────
        var allEvents = caseHubRuntime.eventLog(parentCaseId).toCompletableFuture().join();
        var signals = allEvents.stream()
                               .filter(e -> e.eventType() == CaseHubEventType.SIGNAL_RECEIVED).toList();
        assertThat(signals.stream().anyMatch(s ->
                                                     s.payload() != null && s.payload().toString().contains("reviewFaulted")))
                .as("SIGNAL_RECEIVED with reviewFaulted").isTrue();
    }

    @Test
    void rollbackFailure_mergeConflict_parentTerminatesAfterRollback() {
        testMergeClient.enqueue(
                new MergeOutcome.Success("sha-engine"),
                new MergeOutcome.Failure("merge conflict"));
        testRevertClient.enqueue(
                new RevertOutcome.MergeConflict(101, "branch protection prevents merge"));

        var request = buildRequest(
                new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 10),
                new RepoChangeEntry("casehubio", "platform", 99, "def456", "main", "bob", List.of("src/Config.java"), 10),
                new RepoChangeEntry("casehubio", "work", 7, "ghi789", "main", "carol", List.of("src/Worker.java"), 10));

        // ── Phase 1: Initiation ──────────────────────────────────────
        CoordinatedChangeOutcome outcome      = coordinatedChangeService.start(request);
        UUID                     parentCaseId = outcome.parentCaseId();
        UUID                     reviewA      = outcome.reviewCaseIds().get("casehubio/engine");
        UUID                     reviewB      = outcome.reviewCaseIds().get("casehubio/platform");
        UUID                     reviewC      = outcome.reviewCaseIds().get("casehubio/work");

        // ── Phase 2-3: All reviews complete ──────────────────────────
        preSeedCapabilityKeys(reviewA);
        preSeedCapabilityKeys(reviewB);
        preSeedCapabilityKeys(reviewC);
        driveReviewToCompletion(reviewA);
        driveReviewToCompletion(reviewB);
        driveReviewToCompletion(reviewC);

        awaitCaseStatus(reviewA, CaseStatus.COMPLETED);
        awaitCaseStatus(reviewB, CaseStatus.COMPLETED);
        awaitCaseStatus(reviewC, CaseStatus.COMPLETED);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "allReviewsCompleted")).isEqualTo(true));

        // ── Phase 4a: Merge — stop on failure ────────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "mergeResults")).isNotNull());

        assertThat(testMergeClient.calls()).hasSize(2);
        assertThat(testMergeClient.calls().get(0).repo()).isEqualTo("engine");
        assertThat(testMergeClient.calls().get(1).repo()).isEqualTo("platform");

        // ── Phase 4b: Rollback fires and executes ────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "rollbackResults")).isNotNull());

        assertThat(testRevertClient.calls()).hasSize(1);
        assertThat(testRevertClient.calls().get(0).repo()).isEqualTo("engine");

        // ── Phase 5: Resolve escalation ─────────────────────────────
        // The fact that rollbackResults was written (Phase 4b) proves #164:
        // merge-failed did NOT terminate the case before rollback completed.
        //
        // rollback-human-escalation fires but WorkItem creation fails
        // silently (#165 — casehub-work binary incompatibility). Signal
        // rollbackEscalation directly to complete the chain.
        caseHubRuntime.signal(parentCaseId, "rollbackEscalation",
            Map.of("outcome", "RESOLVED")).toCompletableFuture().join();

        // ── Phase 6: Terminal — merge-failed fires after escalation ──
        awaitCaseTerminal(parentCaseId);
    }

    @Test
    void outOfOrder_reverseCompletion_sameResult() {
        testMergeClient.enqueue(
                new MergeOutcome.Success("sha-engine"),
                new MergeOutcome.Success("sha-platform"));

        var request = buildRequest(
                new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 10),
                new RepoChangeEntry("casehubio", "platform", 99, "def456", "main", "bob", List.of("src/Config.java"), 10));

        CoordinatedChangeOutcome outcome      = coordinatedChangeService.start(request);
        UUID                     parentCaseId = outcome.parentCaseId();
        UUID                     reviewA      = outcome.reviewCaseIds().get("casehubio/engine");
        UUID                     reviewB      = outcome.reviewCaseIds().get("casehubio/platform");

        preSeedCapabilityKeys(reviewA);
        preSeedCapabilityKeys(reviewB);

        // ── Platform completes FIRST ─────────────────────────────────
        driveReviewToCompletion(reviewB);
        awaitCaseStatus(reviewB, CaseStatus.COMPLETED);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "completedReviews.casehubio/platform.status"))
                                                                                                  .isEqualTo("completed"));

        assertThat(contextValue(parentCaseId, "allReviewsCompleted"))
                .as("allReviewsCompleted should NOT be true with only 1 of 2 done").isNull();

        // ── Engine completes SECOND ──────────────────────────────────
        driveReviewToCompletion(reviewA);
        awaitCaseStatus(reviewA, CaseStatus.COMPLETED);

        // ── Same result as happy path ────────────────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "allReviewsCompleted")).isEqualTo(true));

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "mergeResults")).isNotNull());

        awaitCaseStatus(parentCaseId, CaseStatus.COMPLETED);
        assertThat(testMergeClient.calls()).hasSize(2);
    }

    @Test
    void idempotentGuard_extraContextChange_doesNotReFire() {
        testMergeClient.enqueue(
                new MergeOutcome.Success("sha-engine"),
                new MergeOutcome.Failure("merge conflict"));
        testRevertClient.enqueue(
                new RevertOutcome.Success(101, "revert-sha-engine"));

        var request = buildRequest(
                new RepoChangeEntry("casehubio", "engine", 42, "abc123", "main", "alice", List.of("src/Main.java"), 10),
                new RepoChangeEntry("casehubio", "platform", 99, "def456", "main", "bob", List.of("src/Config.java"), 10));

        CoordinatedChangeOutcome outcome      = coordinatedChangeService.start(request);
        UUID                     parentCaseId = outcome.parentCaseId();
        UUID                     reviewA      = outcome.reviewCaseIds().get("casehubio/engine");
        UUID                     reviewB      = outcome.reviewCaseIds().get("casehubio/platform");

        preSeedCapabilityKeys(reviewA);
        preSeedCapabilityKeys(reviewB);
        driveReviewToCompletion(reviewA);
        driveReviewToCompletion(reviewB);

        // ── Wait for merge + rollback to complete ────────────────────
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() ->
                                                                                          assertThat(contextValue(parentCaseId, "rollbackResults")).isNotNull());

        int revertCallsBefore = testRevertClient.calls().size();
        assertThat(revertCallsBefore).isEqualTo(1);

        // ── Parent may already be terminal from merge-failed goal ────
        // Signal only if case is still active — terminal cases reject signals
        var instance = caseInstanceRepository.findByUuid(parentCaseId);
        if (!TERMINAL.contains(instance.getState())) {
            caseHubRuntime.signal(parentCaseId, "probe", "idempotency-check")
                          .toCompletableFuture().join();
        }

        // Assert rollback binding does NOT re-fire — condition guard
        // (.rollbackResults == null) is now false
        await().during(2, SECONDS).atMost(3, SECONDS).pollInterval(200, MILLISECONDS)
               .untilAsserted(() ->
                                      assertThat(testRevertClient.calls()).hasSize(revertCallsBefore));

        // ── Parent reaches terminal state ────────────────────────────
        awaitCaseTerminal(parentCaseId);
    }


}
