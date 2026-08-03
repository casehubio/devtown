package io.casehub.devtown.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewEvent(
    String action,
    Review review,
    PullRequest pull_request,
    Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(long id, String state, User user) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, User user) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login, long id) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(String full_name) {}
}
