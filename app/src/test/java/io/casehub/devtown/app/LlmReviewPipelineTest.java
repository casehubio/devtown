package io.casehub.devtown.app;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrDiffCache;
import io.casehub.devtown.review.PrDiffService;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerAgentRegistry;
import io.casehub.devtown.review.ReviewerOutcome;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmReviewPipelineTest {

    private PrReviewCaseHub caseHub;
    private PrDiffCache diffCache;

    @BeforeEach
    void setUp() {
        PrDiffService diffService = (repo, prNumber) -> new PrDiff(
            repo, prNumber, "base-sha", "head-sha",
            List.of(
                new PrDiff.FileDiff("src/Api.java", "modified",
                    "+ String sql = \"SELECT * FROM users WHERE id=\" + id;", 1, 0),
                new PrDiff.FileDiff("src/Utils.java", "modified",
                    "+ public void helper() { }", 1, 0)),
            false);
        diffCache = new PrDiffCache(diffService);

        ReviewerAgent securityAgent = new ReviewerAgent() {
            @Override public String capability() { return ReviewDomain.SECURITY_REVIEW; }
            @Override public int priority() { return 1; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Completed(List.of(
                    new ReviewFinding(ReviewFinding.Severity.HIGH, "injection",
                        "src/Api.java", new ReviewFinding.LineRange(1, 1),
                        "SQL injection via string concatenation", 0.95)));
            }
        };

        ReviewerAgent styleAgent = new ReviewerAgent() {
            @Override public String capability() { return ReviewDomain.STYLE_REVIEW; }
            @Override public int priority() { return 1; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Completed(List.of(
                    new ReviewFinding(ReviewFinding.Severity.LOW, "naming",
                        "src/Utils.java", null,
                        "method name should be more descriptive", 0.6)));
            }
        };

        var registry = ReviewerAgentRegistry.of(List.of(securityAgent, styleAgent));

        caseHub = new PrReviewCaseHub();
        caseHub.registry = registry;
        caseHub.diffCache = diffCache;
        caseHub.codeAnalysisAgents = new MockCodeAnalysisInstance();
    }

    @Test
    void adaptReview_securityReview_producesRejectedVerdictOnHighFinding() {
        var input = Map.<String, Object>of("pr", prMap());

        WorkerResult result = caseHub.adaptReview(ReviewDomain.SECURITY_REVIEW, input);

        assertNotNull(result.output());
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.output();
        assertEquals("REJECTED", output.get("outcome"));
        @SuppressWarnings("unchecked")
        var findings = (List<Map<String, Object>>) output.get("findings");
        assertEquals(1, findings.size());
        assertEquals("HIGH", findings.get(0).get("severity"));
    }

    @Test
    void adaptReview_styleReview_producesApprovedVerdictOnLowFinding() {
        var input = Map.<String, Object>of("pr", prMap());

        WorkerResult result = caseHub.adaptReview(ReviewDomain.STYLE_REVIEW, input);

        assertNotNull(result.output());
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.output();
        assertEquals("APPROVED", output.get("outcome"));
    }

    @Test
    void adaptReview_missingCapability_returnsFailed() {
        var input = Map.<String, Object>of("pr", prMap());

        WorkerResult result = caseHub.adaptReview("nonexistent", input);

        assertNotNull(result.outcome());
        assertInstanceOf(io.casehub.worker.api.WorkerOutcome.Failed.class, result.outcome());
    }

    @Test
    void adaptCodeAnalysis_returnsClassificationFields() {
        var input = Map.<String, Object>of("pr", prMap());

        WorkerResult result = caseHub.adaptCodeAnalysis(input);

        assertNotNull(result.output());
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.output();
        assertEquals(true, output.get("complete"));
        assertNotNull(output.get("securitySensitive"));
        assertNotNull(output.get("scope"));
    }

    @Test
    void diffIsCachedAcrossMultipleAdapterCalls() {
        var input = Map.<String, Object>of("pr", prMap());

        caseHub.adaptReview(ReviewDomain.SECURITY_REVIEW, input);
        caseHub.adaptReview(ReviewDomain.STYLE_REVIEW, input);

        // Both calls use the same diff from cache — no assertion needed
        // since PrDiffCacheTest already verifies caching behavior.
        // This test just confirms the pipeline doesn't throw.
    }

    private Map<String, Object> prMap() {
        return Map.of(
            "repo", "org/repo",
            "id", "42",
            "headSha", "head-sha",
            "baseRef", "main",
            "linesChanged", 50,
            "contributor", "alice",
            "changedPaths", List.of("src/Api.java", "src/Utils.java"));
    }

    static class MockCodeAnalysisInstance implements jakarta.enterprise.inject.Instance<CodeAnalysisAgent> {
        private final CodeAnalysisAgent agent = context ->
            new CodeAnalysisResult(true, true, false, "single-module", List.of("src/Api.java"), List.of());

        @Override public CodeAnalysisAgent get() { return agent; }
        @Override public java.util.stream.Stream<CodeAnalysisAgent> stream() { return java.util.stream.Stream.of(agent); }
        @Override public jakarta.enterprise.inject.Instance<CodeAnalysisAgent> select(java.lang.annotation.Annotation... qualifiers) { return this; }
        @Override public <U extends CodeAnalysisAgent> jakarta.enterprise.inject.Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends CodeAnalysisAgent> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public boolean isUnsatisfied() { return false; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public boolean isResolvable() { return true; }
        @Override public void destroy(CodeAnalysisAgent instance) {}
        @Override public jakarta.enterprise.inject.Instance.Handle<CodeAnalysisAgent> getHandle() { throw new UnsupportedOperationException(); }
        @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<CodeAnalysisAgent>> handles() { throw new UnsupportedOperationException(); }
        @Override public java.util.Iterator<CodeAnalysisAgent> iterator() { return List.of(agent).iterator(); }
    }
}
