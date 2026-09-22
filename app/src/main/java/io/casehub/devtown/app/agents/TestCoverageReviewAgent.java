package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TestCoverageReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.TEST_COVERAGE;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        return new ReviewerOutcome.Completed(List.of(
                new ReviewFinding(ReviewFinding.Severity.MEDIUM, "coverage",
                                  "src/PaymentService.java", null,
                                  "coverage 67%; payment path untested", 0.7)));
    }
}
