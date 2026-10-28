package io.casehub.devtown.app.github;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(WriteBackEnabledTestProfile.class)
class GitHubWriteBackEnabledTest {

    @Inject
    GitHubCheckRunObserver       checkRunObserver;
    @Inject
    GitHubPrCommentObserver      prCommentObserver;
    @Inject
    GitHubReviewRequestObserver  reviewRequestObserver;
    @Inject
    GitHubSummaryCommentObserver summaryCommentObserver;

    @ConfigProperty(name = "devtown.github.write-back.enabled")
    boolean writeBackEnabled;

    @Test
    void writeBackEnabledWhenConfigSet() {
        assertThat(writeBackEnabled).isTrue();
    }

    @Test
    void allObserversDiscoveredWithWriteBackEnabled() {
        assertThat(checkRunObserver).isNotNull();
        assertThat(prCommentObserver).isNotNull();
        assertThat(reviewRequestObserver).isNotNull();
        assertThat(summaryCommentObserver).isNotNull();
    }
}
