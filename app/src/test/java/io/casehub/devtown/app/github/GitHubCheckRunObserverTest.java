package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubChecksApi;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubCheckRunObserverTest {

    GitHubChecksApi checksApi = mock(GitHubChecksApi.class);
    CrossTenantCaseInstanceRepository caseRepo = mock(CrossTenantCaseInstanceRepository.class);
    GitHubCheckRunObserver observer;

    @BeforeEach
    void setUp() {
        observer = new GitHubCheckRunObserver();
        observer.checksApi = checksApi;
        observer.caseInstanceRepository = caseRepo;
        observer.enabled = true;
    }

    @Test
    void createsCheckRunOnRunning() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L));

        var event = new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);

        verify(checksApi).createCheckRun(eq("owner"), eq("repo"), argThat(body -> {
            assertThat(body.get("name")).isEqualTo("devtown/security-review");
            assertThat(body.get("head_sha")).isEqualTo("abc123");
            assertThat(body.get("status")).isEqualTo("in_progress");
            return true;
        }));
    }

    @Test
    void updatesCheckRunOnCompleted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.RUNNING, TaskStatus.COMPLETED, "tenant1"));

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(12345L), argThat(body -> {
            assertThat(body.get("status")).isEqualTo("completed");
            assertThat(body.get("conclusion")).isEqualTo("success");
            return true;
        }));
    }

    @Test
    void updatesCheckRunOnFaulted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.RUNNING, TaskStatus.FAULTED, "tenant1"));

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(12345L), argThat(body -> {
            assertThat(body.get("status")).isEqualTo("completed");
            assertThat(body.get("conclusion")).isEqualTo("failure");
            return true;
        }));
    }

    @Test
    void skipsNonReviewBindings() {
        var event = new PlanItemStateChangedEvent(
            UUID.randomUUID(), "pi-1", "merge-executor",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);
        verifyNoInteractions(checksApi);
    }

    @Test
    void skipsWhenDisabled() {
        observer.enabled = false;
        var event = new PlanItemStateChangedEvent(
            UUID.randomUUID(), "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);
        verifyNoInteractions(checksApi);
    }

    private void setupCaseInstance(UUID caseId, String repo, String headSha) {
        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(Map.of(
            "pr", Map.of("repo", repo, "id", "42", "headSha", headSha,
                          "baseRef", "main", "linesChanged", 100))));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));
    }
}
