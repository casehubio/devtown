package io.casehub.devtown.domain.trust;

import java.time.Instant;

public interface ContributorHistoryClient {
    ContributorHistorySnapshot fetchHistory(String login, String repo, Instant since);
    RepoMetadataSnapshot fetchRepoMetadata(String owner, String repo);
}
