package io.casehub.devtown.review;

public interface PrReviewApplicationService {
    PrReviewOutcome startReview(PrPayload pr);

    default PrReviewOutcome startReview(PrPayload pr, java.util.Map<String, Object> additionalContext) {
        return startReview(pr);
    }

    LifecycleResult revisePr(String repo, int prNumber, String newHeadSha, int linesChanged);

    LifecycleResult closePr(PrClosePayload close);

    default LifecycleResult signalReviewSubmitted(PrReviewSubmission review) {
        return LifecycleResult.UPDATED;
    }

    LifecycleResult signalCiStatus(String repo, int prNumber, String headSha, long suiteId, String conclusion);

    LifecycleResult signalCheckRun(String repo, int prNumber, String headSha, String checkName, String conclusion, java.time.Instant completedAt);

    SupersedeResult supersedePr(String repo, int oldPrNumber, PrPayload replacement);
}
