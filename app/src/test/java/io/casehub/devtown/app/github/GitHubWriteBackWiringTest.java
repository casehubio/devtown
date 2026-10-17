package io.casehub.devtown.app.github;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GitHubWriteBackWiringTest {

    @Inject GitHubPrCommentObserver prCommentObserver;
    @Inject GitHubCheckRunObserver checkRunObserver;
    @Inject GitHubReviewRequestObserver reviewRequestObserver;
    @Inject GitHubSummaryCommentObserver summaryCommentObserver;

    @Test
    void allObserversAreDiscoveredByCdi() {
        assertThat(prCommentObserver).isNotNull();
        assertThat(checkRunObserver).isNotNull();
        assertThat(reviewRequestObserver).isNotNull();
        assertThat(summaryCommentObserver).isNotNull();
    }

    @Test
    void writeBackIsDisabledByDefault() {
        assertThat(prCommentObserver.enabled).isFalse();
        assertThat(checkRunObserver.enabled).isFalse();
        assertThat(reviewRequestObserver.enabled).isFalse();
        assertThat(summaryCommentObserver.enabled).isFalse();
    }
}
