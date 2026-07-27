package io.casehub.devtown.app;

import io.casehub.devtown.review.PrPayload;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PrReviewCaseServiceAdditionalContextTest {

    @Inject PrReviewCaseService service;
    @Inject PrReviewCaseHub caseHub;

    @Test
    @org.junit.jupiter.api.Disabled("platform SNAPSHOT — missing WorkerProvisioner SPI bean")
    void startReviewWithAdditionalContext_mergesIntoInitialContext() {
        var pr = new PrPayload("casehubio/engine", 42, "abc123", "main", 10, "alice", List.of("src/Main.java"));
        var outcome = service.startReview(pr, Map.of("coordinatedChange", true));
        assertThat(outcome).isNotNull();
        assertThat(outcome.caseId()).isNotNull();

        var coordinated = caseHub.query(outcome.caseId(), "coordinatedChange", Boolean.class)
            ;
        assertThat(coordinated).isTrue();
    }

    @Test
    void startReviewWithoutAdditionalContext_noCoordinatedFlag() {
        var pr = new PrPayload("casehubio/platform", 99, "def456", "main", 5, "bob", List.of("src/App.java"));
        var outcome = service.startReview(pr);
        assertThat(outcome).isNotNull();
        assertThat(outcome.caseId()).isNotNull();

        var coordinated = caseHub.query(outcome.caseId(), "coordinatedChange", Boolean.class)
            ;
        assertThat(coordinated).isNull();
    }
}
