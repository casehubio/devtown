package io.casehub.devtown.domain;

public sealed interface BatchBranchOutcome {
    record Created(String branchName, String tipSha) implements BatchBranchOutcome {}
    record MergeConflict(int conflictPrNumber, String branchName) implements BatchBranchOutcome {}
    record Failure(String reason) implements BatchBranchOutcome {}
}
