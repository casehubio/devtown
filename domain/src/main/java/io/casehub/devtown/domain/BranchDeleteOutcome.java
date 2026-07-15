package io.casehub.devtown.domain;

public sealed interface BranchDeleteOutcome {
    record Deleted(String branchName) implements BranchDeleteOutcome {}
    record NotFound(String branchName) implements BranchDeleteOutcome {}
    record Failure(String reason) implements BranchDeleteOutcome {}
}
