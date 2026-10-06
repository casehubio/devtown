package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StyleReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.STYLE_REVIEW;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        if (context.diff() == null) {return new ReviewerOutcome.Declined("no diff");}

        java.util.List<ReviewFinding> findings         = new java.util.ArrayList<>();
        var                           snakeCasePattern = java.util.regex.Pattern.compile("\\b[a-z]+_[a-z]+\\b");

        for (io.casehub.devtown.review.PrDiff.FileDiff file : context.diff().files()) {
            if (!file.hasReviewablePatch() || !file.path().endsWith(".java")) {continue;}

            String patch = file.patch();
            if (snakeCasePattern.matcher(patch).find()) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.LOW, "naming-convention",
                                               file.path(), null, "snake_case identifier in Java source — use camelCase", 0.70));
            }
        }

        return new ReviewerOutcome.Completed(findings);
    }
}
