package io.casehub.devtown.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestEvent(
    String action,
    int number,
    PullRequest pull_request,
    Repository repository,
    Label label
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
        Head head, Base base, User user,
        boolean draft, boolean merged,
        int additions, int deletions, int changed_files
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Head(String sha) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Base(String ref) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record User(String login) {}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(String full_name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {}
}
