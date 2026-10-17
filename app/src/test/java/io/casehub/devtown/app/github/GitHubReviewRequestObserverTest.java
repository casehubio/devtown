package io.casehub.devtown.app.github;

import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

class GitHubReviewRequestObserverTest {

    GitHubPullRequestApi prApi = mock(GitHubPullRequestApi.class);
    CrossTenantCaseInstanceRepository caseRepo = mock(CrossTenantCaseInstanceRepository.class);
    GitHubReviewRequestObserver observer;

    @BeforeEach
    void setUp() {
        observer = new GitHubReviewRequestObserver();
        observer.prApi = prApi;
        observer.caseInstanceRepository = caseRepo;
        observer.enabled = true;
    }

    @Test
    void requestsReviewersOnHumanGate() {
        UUID caseId = UUID.randomUUID();
        setupCaseInstance(caseId, "owner/repo", 42);

        var event = buildEvent("created", List.of("human-decision:pr-approval"),
            caseId, "reviewer-1");

        observer.onWorkItemCreated(event);

        verify(prApi).requestReviewers(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            @SuppressWarnings("unchecked")
            var reviewers = (List<String>) body.get("reviewers");
            assertThat(reviewers).contains("reviewer-1");
            return true;
        }));
    }

    @Test
    void ignoresNonHumanDecisionEvents() {
        var event = buildEvent("created", List.of("code-analysis"),
            UUID.randomUUID(), "agent-1");
        observer.onWorkItemCreated(event);
        verifyNoInteractions(prApi);
    }

    @Test
    void ignoresNonCreatedEvents() {
        var event = buildEvent("completed", List.of("human-decision:pr-approval"),
            UUID.randomUUID(), "reviewer-1");
        observer.onWorkItemCreated(event);
        verifyNoInteractions(prApi);
    }

    @Test
    void skipsWhenDisabled() {
        observer.enabled = false;
        var event = buildEvent("created", List.of("human-decision:pr-approval"),
            UUID.randomUUID(), "reviewer-1");
        observer.onWorkItemCreated(event);
        verifyNoInteractions(prApi);
    }

    @Test
    void skipsWhenCaseNotFound() {
        UUID caseId = UUID.randomUUID();
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.empty());

        var event = buildEvent("created", List.of("human-decision:pr-approval"),
            caseId, "reviewer-1");
        observer.onWorkItemCreated(event);
        verifyNoInteractions(prApi);
    }

    private void setupCaseInstance(UUID caseId, String repo, int prNumber) {
        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(Map.of(
            "pr", Map.of("repo", repo, "id", String.valueOf(prNumber),
                          "headSha", "abc123", "baseRef", "main", "linesChanged", 100))));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));
    }

    private WorkItemLifecycleEvent buildEvent(String eventName, List<String> types,
                                               UUID caseId, String assigneeId) {
        var workItem = mock(WorkItem.class);
        when(workItem.id()).thenReturn(UUID.randomUUID());
        when(workItem.status()).thenReturn(WorkItemStatus.PENDING);
        when(workItem.tenancyId()).thenReturn("tenant1");
        when(workItem.callerRef()).thenReturn(caseId.toString());
        when(workItem.assigneeId()).thenReturn(assigneeId);
        when(workItem.types()).thenReturn(new java.util.LinkedHashSet<>(types));
        when(workItem.outcome()).thenReturn(null);
        when(workItem.resolution()).thenReturn(null);
        when(workItem.candidateGroups()).thenReturn(null);

        return WorkItemLifecycleEvent.of(eventName, workItem, "system", null);
    }
}
