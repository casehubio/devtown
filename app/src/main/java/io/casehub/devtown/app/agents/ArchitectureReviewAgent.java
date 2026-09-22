package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ArchitectureReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.ARCHITECTURE_REVIEW;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        return new ReviewerOutcome.Declined("distributed transaction outside scope");
    }
}
