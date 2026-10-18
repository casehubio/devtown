package io.casehub.devtown.app.github;

// MapCaseContext is in this package
import io.casehub.devtown.domain.memory.ReviewOutcome;
import io.casehub.devtown.github.GitHubPullRequestApi;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewCompletedEvent;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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

class GitHubPrCommentObserverTest {

    GitHubPullRequestApi prApi = mock(GitHubPullRequestApi.class);
    CrossTenantCaseInstanceRepository caseRepo = mock(CrossTenantCaseInstanceRepository.class);
    GitHubPrCommentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new GitHubPrCommentObserver();
        observer.prApi = prApi;
        observer.caseInstanceRepository = caseRepo;
        observer.enabled = true;
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsReviewWithInlineComments() {
        UUID caseId = UUID.randomUUID();
        var pr = new PrPayload("owner/repo", 42, "abc123", "main", 100, "contributor", 1L, List.of("src/Foo.java"));

        var contextData = new LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42", "headSha", "abc123", "baseRef", "main", "linesChanged", 100));
        var findingMap = new LinkedHashMap<String, Object>();
        findingMap.put("severity", "HIGH");
        findingMap.put("category", "auth");
        findingMap.put("filePath", "src/Foo.java");
        findingMap.put("message", "Token not rotated");
        findingMap.put("confidence", 0.87);
        findingMap.put("startLine", 142);
        findingMap.put("endLine", 142);
        contextData.put("securityReview", Map.of("outcome", "REJECTED", "findings", List.of(findingMap)));

        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(contextData));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));

        var event = new ReviewCompletedEvent(caseId, "tenant1", "security-review",
            "agent-1", ReviewOutcome.COMPLETED, "REJECTED", pr);

        observer.onReviewCompleted(event);

        verify(prApi).createReview(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            assertThat(body.get("event")).isEqualTo("COMMENT");
            var comments = (List<Map<String, Object>>) body.get("comments");
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).get("path")).isEqualTo("src/Foo.java");
            assertThat(comments.get(0).get("line")).isEqualTo(142);
            assertThat((String) comments.get(0).get("body")).contains("HIGH");
            assertThat((String) comments.get(0).get("body")).contains("Token not rotated");
            return true;
        }));
    }

    @Test
    void skipsWhenDisabled() {
        observer.enabled = false;
        var pr = new PrPayload("owner/repo", 42, "abc123", "main", 100, "contributor", 1L, List.of());
        var event = new ReviewCompletedEvent(UUID.randomUUID(), "tenant1", "security-review",
            "agent-1", ReviewOutcome.COMPLETED, "APPROVED", pr);
        observer.onReviewCompleted(event);
        verifyNoInteractions(prApi);
    }

    @Test
    void skipsWhenNoFindings() {
        UUID caseId = UUID.randomUUID();
        var pr = new PrPayload("owner/repo", 42, "abc123", "main", 100, "contributor", 1L, List.of());

        var contextData = new LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42", "headSha", "abc123", "baseRef", "main", "linesChanged", 100));
        contextData.put("styleCheck", Map.of("outcome", "APPROVED", "findings", List.of()));

        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(contextData));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));

        var event = new ReviewCompletedEvent(caseId, "tenant1", "style-review",
            "agent-1", ReviewOutcome.COMPLETED, "APPROVED", pr);
        observer.onReviewCompleted(event);
        verifyNoInteractions(prApi);
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedSeveritySkipsFindingWithoutDroppingOthers() {
        UUID caseId = UUID.randomUUID();
        var pr = new PrPayload("owner/repo", 42, "abc123", "main", 100, "contributor", 1L, List.of("src/Foo.java", "src/Bar.java"));

        var contextData = new LinkedHashMap<String, Object>();
        contextData.put("pr", Map.of("repo", "owner/repo", "id", "42", "headSha", "abc123", "baseRef", "main", "linesChanged", 100));

        var validFinding = new LinkedHashMap<String, Object>();
        validFinding.put("severity", "HIGH");
        validFinding.put("category", "auth");
        validFinding.put("filePath", "src/Foo.java");
        validFinding.put("message", "Token not rotated");
        validFinding.put("confidence", 0.87);
        validFinding.put("startLine", 10);

        var malformedFinding = new LinkedHashMap<String, Object>();
        malformedFinding.put("severity", "BOGUS_SEVERITY");
        malformedFinding.put("category", "misc");
        malformedFinding.put("filePath", "src/Bar.java");
        malformedFinding.put("message", "Something wrong");
        malformedFinding.put("confidence", 0.5);
        malformedFinding.put("startLine", 20);

        contextData.put("securityReview", Map.of("outcome", "REJECTED", "findings", List.of(validFinding, malformedFinding)));

        var instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(new MapCaseContext(contextData));
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.of(instance));

        var event = new ReviewCompletedEvent(caseId, "tenant1", "security-review",
            "agent-1", ReviewOutcome.COMPLETED, "REJECTED", pr);
        observer.onReviewCompleted(event);

        verify(prApi).createReview(eq("owner"), eq("repo"), eq(42), argThat(body -> {
            var comments = (List<Map<String, Object>>) body.get("comments");
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).get("path")).isEqualTo("src/Foo.java");
            return true;
        }));
    }

    @Test
    void skipsWhenCaseNotFound() {
        UUID caseId = UUID.randomUUID();
        var pr = new PrPayload("owner/repo", 42, "abc123", "main", 100, "contributor", 1L, List.of());
        when(caseRepo.findByUuid(caseId)).thenReturn(Optional.empty());

        var event = new ReviewCompletedEvent(caseId, "tenant1", "security-review",
            "agent-1", ReviewOutcome.COMPLETED, "REJECTED", pr);
        observer.onReviewCompleted(event);
        verifyNoInteractions(prApi);
    }
}
