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
        return new ReviewerOutcome.Failed("analysis timed out on large diff");
    }
}
