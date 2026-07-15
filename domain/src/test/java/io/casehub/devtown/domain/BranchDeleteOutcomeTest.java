package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BranchDeleteOutcomeTest {

    @Test
    void deletedCarriesBranchName() {
        var deleted = new BranchDeleteOutcome.Deleted("merge-queue/batch-abc");
        assertThat(deleted.branchName()).isEqualTo("merge-queue/batch-abc");
    }

    @Test
    void notFoundCarriesBranchName() {
        var notFound = new BranchDeleteOutcome.NotFound("merge-queue/batch-abc");
        assertThat(notFound.branchName()).isEqualTo("merge-queue/batch-abc");
    }

    @Test
    void failureCarriesReason() {
        var failure = new BranchDeleteOutcome.Failure("delete failed: HTTP 500");
        assertThat(failure.reason()).isEqualTo("delete failed: HTTP 500");
    }

    @Test
    void exhaustiveSwitchCoversAllCases() {
        BranchDeleteOutcome outcome = new BranchDeleteOutcome.NotFound("b");
        String result = switch (outcome) {
            case BranchDeleteOutcome.Deleted d -> "deleted";
            case BranchDeleteOutcome.NotFound nf -> "not-found";
            case BranchDeleteOutcome.Failure f -> "failure";
        };
        assertThat(result).isEqualTo("not-found");
    }
}
