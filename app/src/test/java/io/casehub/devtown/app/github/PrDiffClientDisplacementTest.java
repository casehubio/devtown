package io.casehub.devtown.app.github;

import io.casehub.devtown.github.GitHubPrDiffClient;
import io.casehub.devtown.review.PrDiffService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PrDiffClientDisplacementTest {

    @Inject PrDiffService prDiffService;

    @Test
    void gitHubPrDiffClientDisplacesDevModeService() {
        assertThat(prDiffService).isInstanceOf(GitHubPrDiffClient.class);
    }
}
