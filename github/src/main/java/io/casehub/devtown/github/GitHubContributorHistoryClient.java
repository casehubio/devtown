package io.casehub.devtown.github;

import io.casehub.devtown.domain.trust.ContributorHistoryClient;
import io.casehub.devtown.domain.trust.ContributorHistorySnapshot;
import io.casehub.devtown.domain.trust.RepoMetadataSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GitHubContributorHistoryClient implements ContributorHistoryClient {

    private final GitHubPullRequestApi prApi;
    private final GitHubRepoApi repoApi;

    @Inject
    public GitHubContributorHistoryClient(@RestClient GitHubPullRequestApi prApi,
                                           @RestClient GitHubRepoApi repoApi) {
        this.prApi = prApi;
        this.repoApi = repoApi;
    }

    // removed — use @Inject constructor for both CDI and test instantiation

    @Override
    public ContributorHistorySnapshot fetchHistory(String login, String repo, Instant since) {
        String[] parts = repo.split("/");
        int merged = 0, closed = 0;
        long numericId = 0;
        Instant oldest = null, newest = null;

        for (int page = 1; ; page++) {
            List<Map<String, Object>> prs = prApi.listPullRequestsByAuthor(
                parts[0], parts[1], login, "all", "created", "desc", 100, page);
            if (prs.isEmpty()) break;

            for (Map<String, Object> pr : prs) {
                Instant createdAt = Instant.parse((String) pr.get("created_at"));
                if (!Instant.MIN.equals(since) && createdAt.isBefore(since)) {
                    return new ContributorHistorySnapshot(login, numericId, repo,
                        merged, closed, oldest, newest);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) pr.get("user");
                if (numericId == 0) numericId = ((Number) user.get("id")).longValue();
                if (newest == null) newest = createdAt;
                oldest = createdAt;

                if (pr.containsKey("merged_at") && pr.get("merged_at") != null) {
                    merged++;
                } else {
                    closed++;
                }
            }
            if (prs.size() < 100) break;
        }
        return new ContributorHistorySnapshot(login, numericId, repo,
            merged, closed, oldest, newest);
    }

    @Override
    public RepoMetadataSnapshot fetchRepoMetadata(String owner, String repo) {
        Map<String, Object> data = repoApi.getRepository(owner, repo);
        List<Map<String, Object>> contributors = repoApi.listContributors(owner, repo, 100, "false");
        return new RepoMetadataSnapshot(
            owner + "/" + repo,
            ((Number) data.get("stargazers_count")).intValue(),
            contributors.size(),
            0,
            Instant.parse((String) data.get("created_at")),
            Instant.parse((String) data.get("pushed_at")));
    }
}
