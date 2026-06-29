package io.casehub.devtown.github;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubPayloadMapperTest {

    private final GitHubPullRequestEvent event = new GitHubPullRequestEvent(
        "opened", 42,
        new GitHubPullRequestEvent.PullRequest(
            new GitHubPullRequestEvent.PullRequest.Head("abc123"),
            new GitHubPullRequestEvent.PullRequest.Base("main"),
            new GitHubPullRequestEvent.PullRequest.User("octocat"),
            false, false, 100, 50, 5
        ),
        new GitHubPullRequestEvent.Repository("casehubio/devtown"),
        null
    );

    @Test
    void mapsRepo() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.repo()).isEqualTo("casehubio/devtown");
    }

    @Test
    void mapsPrNumber() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.prNumber()).isEqualTo(42);
    }

    @Test
    void mapsHeadSha() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.headSha()).isEqualTo("abc123");
    }

    @Test
    void mapsBaseRef() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.baseRef()).isEqualTo("main");
    }

    @Test
    void mapsLinesChangedAsSum() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.linesChanged()).isEqualTo(150);
    }

    @Test
    void mapsContributor() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.contributor()).isEqualTo("octocat");
    }

    @Test
    void changedPathsIsEmptyList() {
        var payload = GitHubPayloadMapper.toPrPayload(event);
        assertThat(payload.changedPaths()).isEmpty();
    }
}
