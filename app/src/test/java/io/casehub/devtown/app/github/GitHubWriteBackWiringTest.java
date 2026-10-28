package io.casehub.devtown.app.github;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GitHubWriteBackWiringTest {

    @Inject
    GitHubPrCommentObserver      prCommentObserver;
    @Inject
    GitHubCheckRunObserver       checkRunObserver;
    @Inject
    GitHubReviewRequestObserver  reviewRequestObserver;
    @Inject
    GitHubSummaryCommentObserver summaryCommentObserver;

    @ConfigProperty(name = "devtown.github.write-back.enabled", defaultValue = "false")
    boolean writeBackEnabled;

    @Test
    void allObserversAreDiscoveredByCdi() {
        assertThat(prCommentObserver).isNotNull();
        assertThat(checkRunObserver).isNotNull();
        assertThat(reviewRequestObserver).isNotNull();
        assertThat(summaryCommentObserver).isNotNull();
    }

    @Test
    void writeBackIsDisabledByDefault() {
        assertThat(writeBackEnabled).isFalse();
    }
}
