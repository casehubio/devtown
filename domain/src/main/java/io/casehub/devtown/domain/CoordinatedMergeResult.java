package io.casehub.devtown.domain;

public sealed interface CoordinatedMergeResult {
    String repo();
    record Success(String repo, String mergeSha) implements CoordinatedMergeResult {}
    record Failure(String repo, String reason) implements CoordinatedMergeResult {}
}
