package io.casehub.devtown.review;

public interface PrReviewApplicationService {
    PrReviewOutcome startReview(PrPayload pr);
    LifecycleResult revisePr(String repo, int prNumber, String newHeadSha, int linesChanged);
    LifecycleResult closePr(String repo, int prNumber, boolean merged);
}
