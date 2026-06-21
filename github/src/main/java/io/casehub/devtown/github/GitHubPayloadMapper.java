package io.casehub.devtown.github;

import io.casehub.devtown.review.PrPayload;
import java.util.List;

public final class GitHubPayloadMapper {

    private GitHubPayloadMapper() {}

    public static PrPayload toPrPayload(GitHubPullRequestEvent event) {
        var pr = event.pull_request();
        return new PrPayload(
            event.repository().full_name(),
            event.number(),
            pr.head().sha(),
            pr.base().ref(),
            pr.additions() + pr.deletions(),
            pr.user().login(),
            List.of()
        );
    }
}
