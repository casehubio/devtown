package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.ContributorHistoryClient;
import io.casehub.devtown.domain.trust.ContributorHistorySnapshot;
import io.casehub.devtown.domain.trust.RepoMetadataSnapshot;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
@DefaultBean
public class NoOpContributorHistoryClient implements ContributorHistoryClient {

    @Override
    public ContributorHistorySnapshot fetchHistory(String login, String repo, Instant since) {
        return new ContributorHistorySnapshot(login, 0, repo, 0, 0, null, null);
    }

    @Override
    public RepoMetadataSnapshot fetchRepoMetadata(String owner, String repo) {
        return new RepoMetadataSnapshot(owner + "/" + repo, 0, 0, 0, Instant.now(), Instant.now());
    }
}
