package io.casehub.devtown.review;

public record BootstrapContributorEvent(
    String login,
    long contributorNumericId,
    String repo
) {}
