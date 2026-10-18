package io.casehub.devtown.app.github;

import io.casehub.api.model.TaskStatus;
import io.casehub.devtown.github.GitHubChecksApi;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @SuppressWarnings("unchecked")
    @Test
    void createsCheckRunOnRunning() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L))
            .thenReturn(Map.of("id", 99999L));

        var event = new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1");
        observer.onPlanItemStateChanged(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(checksApi, times(2)).createCheckRun(eq("owner"), eq("repo"), captor.capture());
        var bindingCall = captor.getAllValues().get(0);
        assertThat(bindingCall.get("name")).isEqualTo("devtown/security-review");
        assertThat(bindingCall.get("head_sha")).isEqualTo("abc123");
        assertThat(bindingCall.get("status")).isEqualTo("in_progress");
    }

    @Test
    void updatesCheckRunOnCompleted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L))
            .thenReturn(Map.of("id", 99999L));

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
            .thenReturn(Map.of("id", 12345L))
            .thenReturn(Map.of("id", 99999L));

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
    void usesActionRequiredWhenHighSeverityFindings() {
        UUID caseId = UUID.randomUUID();
        var contextData = new java.util.LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42", "headSha", "abc123",
                          "baseRef", "main", "linesChanged", 100));
        var finding = Map.of("severity", "HIGH", "category", "auth",
            "filePath", "src/Foo.java", "message", "Token not rotated", "confidence", 0.87);
        contextData.put("securityReview", Map.of("outcome", "REJECTED", "findings", java.util.List.of(finding)));
        setupCaseInstanceWithContext(caseId, contextData);

        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L))
            .thenReturn(Map.of("id", 99999L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.RUNNING, TaskStatus.COMPLETED, "tenant1"));

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(12345L), argThat(body -> {
            assertThat(body.get("conclusion")).isEqualTo("action_required");
            return true;
        }));
    }

    @Test
    void usesSuccessWhenOnlyLowSeverityFindings() {
        UUID caseId = UUID.randomUUID();
        var contextData = new java.util.LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42", "headSha", "abc123",
                          "baseRef", "main", "linesChanged", 100));
        var finding = Map.of("severity", "LOW", "category", "style",
            "filePath", "src/Foo.java", "message", "Minor style issue", "confidence", 0.5);
        contextData.put("securityReview", Map.of("outcome", "APPROVED", "findings", java.util.List.of(finding)));
        setupCaseInstanceWithContext(caseId, contextData);

        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 12345L))
            .thenReturn(Map.of("id", 99999L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.RUNNING, TaskStatus.COMPLETED, "tenant1"));

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(12345L), argThat(body -> {
            assertThat(body.get("conclusion")).isEqualTo("success");
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

    @SuppressWarnings("unchecked")
    @Test
    void createsAggregateCheckRunOnFirstBinding() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 100L))
            .thenReturn(Map.of("id", 200L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(checksApi, times(2)).createCheckRun(eq("owner"), eq("repo"), captor.capture());

        var calls = captor.getAllValues();
        assertThat(calls.get(0).get("name")).isEqualTo("devtown/security-review");
        assertThat(calls.get(1).get("name")).isEqualTo("devtown/review-status");
        assertThat(calls.get(1).get("status")).isEqualTo("in_progress");
    }

    @SuppressWarnings("unchecked")
    @Test
    void completesAggregateCheckRunOnCaseCompleted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 100L))
            .thenReturn(Map.of("id", 200L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "CompleteCase", "CaseCompleted",
            "COMPLETED", null, null, null);
        observer.onCaseLifecycle(event);

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(200L), argThat(body -> {
            assertThat(body.get("status")).isEqualTo("completed");
            assertThat(body.get("conclusion")).isEqualTo("success");
            return true;
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    void failsAggregateCheckRunOnCaseFaulted() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", "abc123");
        when(checksApi.createCheckRun(eq("owner"), eq("repo"), any()))
            .thenReturn(Map.of("id", 100L))
            .thenReturn(Map.of("id", 200L));

        observer.onPlanItemStateChanged(new PlanItemStateChangedEvent(
            caseId, "pi-1", "security-review",
            TaskStatus.PENDING, TaskStatus.RUNNING, "tenant1"));

        var event = CaseLifecycleEvent.of(
            caseId, "tenant1", "FaultCase", "CaseFaulted",
            "FAULTED", null, null, null);
        observer.onCaseLifecycle(event);

        verify(checksApi).updateCheckRun(eq("owner"), eq("repo"), eq(200L), argThat(body -> {
            assertThat(body.get("status")).isEqualTo("completed");
            assertThat(body.get("conclusion")).isEqualTo("failure");
            return true;
        }));
    }

    private void setupCaseInstance(UUID caseId, String repo, String headSha) {
        setupCaseInstanceWithContext(caseId, Map.of(
            "pr", Map.of("repo", repo, "id", "42", "headSha", headSha,
                          "baseRef", "main", "linesChanged", 100)));
    }

    private void setupCaseInstanceWithContext(UUID caseId, Map<String, Object> contextData) {
        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(contextData));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));
    }
}
