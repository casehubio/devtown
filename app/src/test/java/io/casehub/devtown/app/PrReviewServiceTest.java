package io.casehub.devtown.app;

import io.casehub.devtown.review.LifecycleResult;
import io.casehub.devtown.review.PrPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrReviewServiceTest {

    private final PrReviewService service = new PrReviewService();

    @Test
    void startReview_returnsNonNullOutcome() {
        var pr = new PrPayload("casehubio/devtown", 42, "abc123", "main", 150, "test-contributor", List.of());
        var outcome = service.startReview(pr);
        assertThat(outcome).isNotNull();
    }

    @Test
    void startReview_verdictIsNonBlank() {
        var pr = new PrPayload("casehubio/devtown", 42, "abc123", "main", 150, "test-contributor", List.of());
        var outcome = service.startReview(pr);
        assertThat(outcome.verdict()).isNotBlank();
    }

    @Test
    void startReview_findingsIsNonNull() {
        var pr = new PrPayload("casehubio/devtown", 42, "abc123", "main", 150, "test-contributor", List.of());
        var outcome = service.startReview(pr);
        assertThat(outcome.findings()).isNotNull();
    }

    @Test
    void revisePr_returnsNoActiveCase() {
        var result = service.revisePr("casehubio/devtown", 42, "newsha", 200);
        assertThat(result).isEqualTo(LifecycleResult.NO_ACTIVE_CASE);
    }

    @Test
    void closePr_returnsNoActiveCase() {
        var result = service.closePr("casehubio/devtown", 42, false);
        assertThat(result).isEqualTo(LifecycleResult.NO_ACTIVE_CASE);
    }
}
