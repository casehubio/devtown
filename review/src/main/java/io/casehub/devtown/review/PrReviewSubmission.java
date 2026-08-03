package io.casehub.devtown.review;

public record PrReviewSubmission(
        String repo,
        int prNumber,
        String reviewState,
        long reviewId,
        String contributorLogin,
        long contributorNumericId
) {}
