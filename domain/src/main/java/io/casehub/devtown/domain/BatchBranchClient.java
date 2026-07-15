package io.casehub.devtown.domain;

import java.util.List;

public interface BatchBranchClient {

    BatchBranchOutcome createBatchBranch(
        String owner, String repo,
        String targetBranch, String batchId,
        List<PrRef> prs
    );

    BranchDeleteOutcome deleteBatchBranch(
        String owner, String repo,
        String branchName
    );
}
