package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(Map.of(
            "pr", Map.of("repo", repo, "id", String.valueOf(prNumber),
                          "headSha", "abc123", "baseRef", "main", "linesChanged", 100))));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));
    }
}
