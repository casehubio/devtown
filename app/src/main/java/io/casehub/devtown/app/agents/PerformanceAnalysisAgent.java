package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.PrPayload;
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
    public ReviewerOutcome handle(final PrPayload pr) {
        return new ReviewerOutcome.Failed("analysis timed out on large diff");
    }
}
