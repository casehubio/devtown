package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BatchBranchOutcomeTest {

    @Test
    void createdCarriesBranchNameAndTipSha() {
        var created = new BatchBranchOutcome.Created("merge-queue/batch-abc", "sha123");
        assertThat(created.branchName()).isEqualTo("merge-queue/batch-abc");
        assertThat(created.tipSha()).isEqualTo("sha123");
    }

    @Test
    void mergeConflictCarriesPrNumberAndBranchName() {
        var conflict = new BatchBranchOutcome.MergeConflict(42, "merge-queue/batch-abc");
        assertThat(conflict.conflictPrNumber()).isEqualTo(42);
        assertThat(conflict.branchName()).isEqualTo("merge-queue/batch-abc");
    }

    @Test
    void failureCarriesReason() {
        var failure = new BatchBranchOutcome.Failure("api error: HTTP 500");
        assertThat(failure.reason()).isEqualTo("api error: HTTP 500");
    }

    @Test
    void exhaustiveSwitchCoversAllCases() {
        BatchBranchOutcome outcome = new BatchBranchOutcome.Created("b", "s");
        String result = switch (outcome) {
            case BatchBranchOutcome.Created c -> "created:" + c.branchName();
            case BatchBranchOutcome.MergeConflict mc -> "conflict:" + mc.conflictPrNumber();
            case BatchBranchOutcome.Failure f -> "failure:" + f.reason();
        };
        assertThat(result).isEqualTo("created:b");
    }
}
