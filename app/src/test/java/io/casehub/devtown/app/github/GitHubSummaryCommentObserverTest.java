package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubSummaryCommentObserverTest {

    GitHubPullRequestApi prApi = mock(GitHubPullRequestApi.class);
    CrossTenantCaseInstanceRepository caseRepo = mock(CrossTenantCaseInstanceRepository.class);
    GitHubSummaryCommentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new GitHubSummaryCommentObserver();
        observer.prApi = prApi;
        observer.caseInstanceRepository = caseRepo;
        observer.enabled = true;
    }

    @Test
    void postsDispatchSummaryOnFirstWorkerStart() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        var event = new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);

        verify(prApi).createIssueComment(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            String bodyText = (String) body.get("body");
            assertThat(bodyText).contains("security-review");
            assertThat(bodyText).contains("dispatched");
            return true;
        }));
    }

    @Test
    void doesNotPostDuplicateDispatchSummary() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-2", "style-check",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        verify(prApi, times(1)).createIssueComment(any(), any(), anyInt(), any());
    }

    @Test
    void postsCompletionSummary() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "CompleteCase", "CaseCompleted",
            "COMPLETED", null, null, null);
        observer.onCaseLifecycle(event);

        verify(prApi).createIssueComment(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            String bodyText = (String) body.get("body");
            assertThat(bodyText).contains("completed");
            return true;
        }));
    }

    @Test
    void postsCancelledSummary() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "CancelCase", "CaseCancelled",
            "CANCELLED", null, null, null);
        observer.onCaseLifecycle(event);

        verify(prApi).createIssueComment(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            String bodyText = (String) body.get("body");
            assertThat(bodyText).containsIgnoringCase("cancel");
            return true;
        }));
    }

    @Test
    void skipsWhenDisabled() {
        observer.enabled = false;
        var event = new PlanItemStateChangedEvent(
            UUID.randomUUID(), "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);
        verifyNoInteractions(prApi);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleansMapsOnFaulted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "FaultCase", "CaseFaulted",
            "FAULTED", null, null, null);
        observer.onCaseLifecycle(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(prApi, times(2)).createIssueComment(eq("owner"), eq("repo"), eq(42), captor.capture());
        String lifecycleBody = (String) captor.getAllValues().get(1).get("body");
        assertThat(lifecycleBody).containsIgnoringCase("faulted");

        // After FAULTED, a new case should be able to post a fresh dispatch summary
        UUID newCaseId = UUID.randomUUID();
        setupCaseInstance(newCaseId, "owner/repo", 43);
        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            newCaseId, "pi-2", "style-check",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));
        verify(prApi).createIssueComment(eq("owner"), eq("repo"), eq(43), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleansMapsOnObsolete() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "ObsoleteCase", "CaseObsolete",
            "OBSOLETE", null, null, null);
        observer.onCaseLifecycle(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(prApi, times(2)).createIssueComment(eq("owner"), eq("repo"), eq(42), captor.capture());
        String lifecycleBody = (String) captor.getAllValues().get(1).get("body");
        assertThat(lifecycleBody).containsIgnoringCase("obsolete");
    }

    @Test
    void completionSummaryIncludesSeverityCounts() {
        UUID caseId = UUID.randomUUID();
        var contextData = new LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42",
            "headSha", "abc123", "baseRef", "main", "linesChanged", 100));
        contextData.put("securityReview", Map.of("outcome", "REJECTED", "findings", List.of(
            Map.of("severity", "HIGH", "message", "Token not rotated"),
            Map.of("severity", "CRITICAL", "message", "SQL injection"))));
        contextData.put("styleCheck", Map.of("outcome", "APPROVED", "findings", List.of(
            Map.of("severity", "LOW", "message", "Spacing issue"))));
        setupCaseInstanceWithContext(caseId, "owner/repo", 42, contextData);

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "CompleteCase", "CaseCompleted",
            "COMPLETED", null, null, null);
        observer.onCaseLifecycle(event);

        verify(prApi).createIssueComment(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            String bodyText = (String) body.get("body");
            assertThat(bodyText).contains("CRITICAL: 1");
            assertThat(bodyText).contains("HIGH: 1");
            assertThat(bodyText).contains("LOW: 1");
            return true;
        }));
    }

    @Test
    void ignoresNonTerminalCaseEvents() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "StartCase", "CaseStarted",
            "ACTIVE", null, null, null);
        observer.onCaseLifecycle(event);
        verifyNoInteractions(prApi);
    }

    private void setupCaseInstance(UUID caseId, String repo, int prNumber) {
        setupCaseInstanceWithContext(caseId, repo, prNumber, Map.of(
            "pr", Map.of("repo", repo, "id", String.valueOf(prNumber),
                          "headSha", "abc123", "baseRef", "main", "linesChanged", 100)));
    }

    private void setupCaseInstanceWithContext(UUID caseId, String repo, int prNumber, Map<String, Object> contextData) {
        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(contextData));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));
    }
}
