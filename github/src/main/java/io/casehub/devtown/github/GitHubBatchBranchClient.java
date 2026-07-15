package io.casehub.devtown.github;

import io.casehub.devtown.domain.BatchBranchClient;
import io.casehub.devtown.domain.BatchBranchOutcome;
import io.casehub.devtown.domain.BranchDeleteOutcome;
import io.casehub.devtown.domain.PrRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GitHubBatchBranchClient implements BatchBranchClient {

    private final GitHubGitApi gitApi;

    public GitHubBatchBranchClient(@RestClient GitHubGitApi gitApi) {
        this.gitApi = gitApi;
    }

    @Override
    public BatchBranchOutcome createBatchBranch(
            String owner, String repo,
            String targetBranch, String batchId,
            List<PrRef> prs) {
        try {
            String baseSha = gitApi.getRef(owner, repo, "heads/" + targetBranch).sha();
            if (baseSha == null) {
                return new BatchBranchOutcome.Failure(
                    "target branch '" + targetBranch + "' has no resolvable SHA");
            }

            String branchName = "merge-queue/batch-" + batchId;
            try {
                gitApi.deleteRef(owner, repo, "heads/" + branchName);
            } catch (WebApplicationException ignored) {
            }

            gitApi.createRef(owner, repo,
                Map.of("ref", "refs/heads/" + branchName, "sha", baseSha));

            for (PrRef pr : prs) {
                try {
                    gitApi.merge(owner, repo,
                        Map.of("base", branchName, "head", pr.headSha(),
                               "commit_message", "Merge PR #" + pr.number() + " into " + branchName));
                } catch (WebApplicationException e) {
                    if (e.getResponse().getStatus() == 409) {
                        return new BatchBranchOutcome.MergeConflict(pr.number(), branchName);
                    }
                    return new BatchBranchOutcome.Failure(
                        "merge failed for PR #" + pr.number() + ": HTTP " + e.getResponse().getStatus());
                }
            }

            String tipSha = gitApi.getRef(owner, repo, "heads/" + branchName).sha();
            if (tipSha == null) {
                return new BatchBranchOutcome.Failure(
                    "batch branch '" + branchName + "' has no resolvable SHA after merges");
            }
            return new BatchBranchOutcome.Created(branchName, tipSha);

        } catch (WebApplicationException e) {
            return new BatchBranchOutcome.Failure("api error: HTTP " + e.getResponse().getStatus());
        } catch (Exception e) {
            return new BatchBranchOutcome.Failure("api error: " + e.getMessage());
        }
    }

    @Override
    public BranchDeleteOutcome deleteBatchBranch(String owner, String repo, String branchName) {
        try {
            gitApi.deleteRef(owner, repo, "heads/" + branchName);
            return new BranchDeleteOutcome.Deleted(branchName);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 422) {
                return new BranchDeleteOutcome.NotFound(branchName);
            }
            return new BranchDeleteOutcome.Failure("delete failed: HTTP " + e.getResponse().getStatus());
        } catch (Exception e) {
            return new BranchDeleteOutcome.Failure("delete failed: " + e.getMessage());
        }
    }
}
