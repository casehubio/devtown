package io.casehub.devtown.app;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.PrFinding;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.PrReviewOutcome;
import io.casehub.devtown.review.PrVerdict;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@DefaultBean
public class NaivePrReviewService implements PrReviewApplicationService {

    @Override
    public PrReviewOutcome review(PrPayload pr) {
        // LAYER 1 GAP: no attribution — which agent ran this analysis? No record.
        var securityFindings = analyzeSecurityDirectly(pr);
        // LAYER 1 GAP: no response SLA — analysis can stall indefinitely with no escalation.
        var architectureFindings = reviewArchitectureDirectly(pr);
        // LAYER 1 GAP: no formal DECLINE — if a specialist can't review, it silently fails or errors.
        // LAYER 1 GAP: no tamper-evident audit trail — cannot trace a production incident to this review.
        // LAYER 1 GAP: no trust weighting — a novice and an expert are treated identically.
        var allFindings = new ArrayList<PrFinding>(securityFindings);
        allFindings.addAll(architectureFindings);
        return new PrReviewOutcome(PrVerdict.REVIEWED, allFindings);
    }

    private List<PrFinding> analyzeSecurityDirectly(PrPayload pr) {
        return List.of(new PrFinding(ReviewDomain.SECURITY_REVIEW, "security-analysis-complete"));
    }

    private List<PrFinding> reviewArchitectureDirectly(PrPayload pr) {
        return List.of(new PrFinding(ReviewDomain.ARCHITECTURE_REVIEW, "architecture-review-complete"));
    }
}
