package io.casehub.devtown.app.agents;

import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevModeReviewerAgentsTest {

    // ── SecurityReviewAgent ──

    @Test
    void securityAgent_producesFindings_forAuthPaths() {
        var agent = new SecurityReviewAgent();
        var diff = diffWith(
            fileDiff("src/auth/TokenService.java",
                "@@ -130,6 +130,10 @@\n+    public String issueToken(User user) {\n+        return jwt;\n+    }"),
            fileDiff("src/auth/SessionManager.java",
                "@@ -45,8 +45,15 @@\n+    session.setIpAddress(request.getRemoteAddr());")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().allMatch(f -> f.filePath().contains("auth/")));
    }

    @Test
    void securityAgent_declines_forNonSecurityPaths() {
        var agent = new SecurityReviewAgent();
        var diff = diffWith(
            fileDiff("src/util/StringUtils.java", "@@ -1,3 +1,5 @@\n+    return s.trim();")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Declined.class, outcome);
    }

    // ── ArchitectureReviewAgent ──

    @Test
    void architectureAgent_producesFindings_forLargeCrossingPr() {
        var agent = new ArchitectureReviewAgent();
        var diff = diffWith(
            fileDiff("src/payment/PaymentService.java", "@@ -1 +1,200 @@\n+    pay();", 400, 0),
            fileDiff("src/order/OrderService.java", "@@ -1 +1,200 @@\n+    order();", 400, 0),
            fileDiff("payment-module/pom.xml", "@@ -0,0 +1,50 @@\n+<project>", 50, 0),
            fileDiff("docs/architecture.md", "@@ -1 +1,20 @@\n+    updated", 20, 0)
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        assertFalse(((ReviewerOutcome.Completed) outcome).findings().isEmpty());
    }

    @Test
    void architectureAgent_declines_forSmallPr() {
        var agent = new ArchitectureReviewAgent();
        var diff = diffWith(
            fileDiff("src/util/Helper.java", "@@ -1,3 +1,5 @@\n+    return x;", 5, 0)
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Declined.class, outcome);
    }

    // ── StyleReviewAgent ──

    @Test
    void styleAgent_producesFindings_forNamingViolations() {
        var agent = new StyleReviewAgent();
        var diff = diffWith(
            fileDiff("src/service/UserService.java",
                "@@ -10,3 +10,8 @@\n+    public void do_something() {\n+        int my_var = 1;\n+    }")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertFalse(findings.isEmpty());
    }

    // ── TestCoverageReviewAgent ──

    @Test
    void testCoverage_findsMissingTests() {
        var agent = new TestCoverageReviewAgent();
        var diff = diffWith(
            fileDiff("src/payment/PaymentService.java", "@@ -1 +1,20 @@\n+    pay();"),
            fileDiff("src/payment/GatewayAdapter.java", "@@ -1 +1,20 @@\n+    connect();")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertTrue(findings.stream().anyMatch(f -> f.category().equals("missing-test")));
    }

    @Test
    void testCoverage_noFindings_whenTestsExist() {
        var agent = new TestCoverageReviewAgent();
        var diff = diffWith(
            fileDiff("src/payment/PaymentService.java", "@@ -1 +1,20 @@\n+    pay();"),
            fileDiff("test/payment/PaymentServiceTest.java", "@@ -1 +1,20 @@\n+    @Test void testPay() {}")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        var findings = ((ReviewerOutcome.Completed) outcome).findings();
        assertTrue(findings.stream().noneMatch(f ->
            f.filePath().equals("src/payment/PaymentService.java")));
    }

    // ── PerformanceAnalysisAgent ──

    @Test
    void performanceAgent_producesFindings_notFailure() {
        var agent = new PerformanceAnalysisAgent();
        var diff = diffWith(
            fileDiff("src/service/DataService.java",
                "@@ -50,3 +50,10 @@\n+    for (Order o : orders) {\n+        db.query(o.id());\n+    }")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
    }

    @Test
    void performanceAgent_completesWithEmptyFindings_forCleanCode() {
        var agent = new PerformanceAnalysisAgent();
        var diff = diffWith(
            fileDiff("src/util/Helper.java", "@@ -1,3 +1,5 @@\n+    return x.trim();")
        );
        var outcome = agent.handle(new ReviewContext(samplePr(), diff));

        assertInstanceOf(ReviewerOutcome.Completed.class, outcome);
        assertTrue(((ReviewerOutcome.Completed) outcome).findings().isEmpty());
    }

    // ── Helpers ──

    private static PrPayload samplePr() {
        return new PrPayload("org/repo", 1, "abc", "main", 100, "dev", 1L, List.of());
    }

    private static PrDiff diffWith(PrDiff.FileDiff... files) {
        return new PrDiff("org/repo", 1, "base", "head", List.of(files), false);
    }

    private static PrDiff.FileDiff fileDiff(String path, String patch) {
        return new PrDiff.FileDiff(path, "modified", patch, 2, 0);
    }

    private static PrDiff.FileDiff fileDiff(String path, String patch, int additions, int deletions) {
        return new PrDiff.FileDiff(path, "modified", patch, additions, deletions);
    }
}
