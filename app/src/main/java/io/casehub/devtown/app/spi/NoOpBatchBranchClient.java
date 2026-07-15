package io.casehub.devtown.app.spi;

import io.casehub.devtown.domain.BatchBranchClient;
import io.casehub.devtown.domain.BatchBranchOutcome;
import io.casehub.devtown.domain.BranchDeleteOutcome;
import io.casehub.devtown.domain.PrRef;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpBatchBranchClient implements BatchBranchClient {

    @Override
    public BatchBranchOutcome createBatchBranch(
            String owner, String repo,
            String targetBranch, String batchId,
            List<PrRef> prs) {
        return new BatchBranchOutcome.Failure("no batch branch client configured");
    }

    @Override
    public BranchDeleteOutcome deleteBatchBranch(String owner, String repo, String branchName) {
        return new BranchDeleteOutcome.Failure("no batch branch client configured");
    }
}
