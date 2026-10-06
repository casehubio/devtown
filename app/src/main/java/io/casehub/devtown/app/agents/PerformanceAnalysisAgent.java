package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PerformanceAnalysisAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.PERFORMANCE_ANALYSIS;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        if (context.diff() == null) {return new ReviewerOutcome.Declined("no diff");}

        java.util.List<io.casehub.devtown.domain.ReviewFinding> findings = new java.util.ArrayList<>();

        for (io.casehub.devtown.review.PrDiff.FileDiff file : context.diff().files()) {
            if (!file.hasReviewablePatch()) {continue;}

            String patch = file.patch();
            if (patch.contains("for (") && patch.contains(".query(")) {
                findings.add(new io.casehub.devtown.domain.ReviewFinding(
                        io.casehub.devtown.domain.ReviewFinding.Severity.HIGH, "n-plus-one",
                        file.path(), null, "Potential N+1 query pattern — query inside loop", 0.80));
            }
            if (patch.contains("findAll()") || patch.contains("SELECT *")) {
                findings.add(new io.casehub.devtown.domain.ReviewFinding(
                        io.casehub.devtown.domain.ReviewFinding.Severity.MEDIUM, "unbounded-query",
                        file.path(), null, "Unbounded query — consider pagination or limits", 0.70));
            }
        }

        return new ReviewerOutcome.Completed(findings);
    }
}
