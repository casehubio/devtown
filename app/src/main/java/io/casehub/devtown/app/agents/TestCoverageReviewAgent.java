package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestCoverageReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.TEST_COVERAGE;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        if (context.diff() == null) {return new ReviewerOutcome.Declined("no diff");}

        java.util.Set<String> sourceFiles = new java.util.HashSet<>();
        java.util.Set<String> testFiles   = new java.util.HashSet<>();

        for (io.casehub.devtown.review.PrDiff.FileDiff file : context.diff().files()) {
            if (file.path().contains("test/") || file.path().contains("Test.java")) {
                testFiles.add(file.path());
            } else if (file.path().endsWith(".java") && file.path().contains("src/")) {
                sourceFiles.add(file.path());
            }
        }

        java.util.List<ReviewFinding> findings = new java.util.ArrayList<>();
        for (String src : sourceFiles) {
            String  className = src.substring(src.lastIndexOf('/') + 1).replace(".java", "");
            boolean hasTest   = testFiles.stream().anyMatch(t -> t.contains(className + "Test"));
            if (!hasTest) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.MEDIUM, "missing-test",
                                               src, null, "No corresponding test file for " + className, 0.75));
            }
        }

        return new ReviewerOutcome.Completed(findings);
    }
}
