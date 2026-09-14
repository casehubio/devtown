package io.casehub.devtown.github;

import io.casehub.devtown.domain.trust.ContributorHistorySnapshot;
import io.casehub.devtown.domain.trust.RepoMetadataSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubContributorHistoryClientTest {

    private GitHubContributorHistoryClient client;

    @BeforeEach
    void setUp() {
        var prApi = new StubPullRequestApi();
        var repoApi = new StubRepoApi();
        client = new GitHubContributorHistoryClient(prApi, repoApi);
    }

    @Test
    void fetchHistory_countsMergedAndClosed() {
        ContributorHistorySnapshot snapshot = client.fetchHistory("alice", "org/repo", Instant.MIN);
        assertEquals("alice", snapshot.login());
        assertEquals(123L, snapshot.contributorNumericId());
        assertEquals("org/repo", snapshot.repo());
        assertEquals(2, snapshot.mergedCount());
        assertEquals(1, snapshot.closedCount());
    }

    @Test
    void fetchHistory_tracksOldestAndNewest() {
        ContributorHistorySnapshot snapshot = client.fetchHistory("alice", "org/repo", Instant.MIN);
        assertNotNull(snapshot.oldestPrAt());
        assertNotNull(snapshot.newestPrAt());
        assertTrue(snapshot.newestPrAt().isAfter(snapshot.oldestPrAt()));
    }

    @Test
    void fetchHistory_incrementalSince_filtersOldPrs() {
        Instant                    since    = Instant.parse("2026-02-01T00:00:00Z");
        ContributorHistorySnapshot snapshot = client.fetchHistory("alice", "org/repo", since);
        assertEquals(1, snapshot.mergedCount());
        assertEquals(0, snapshot.closedCount());
    }

    @Test
    void fetchRepoMetadata_extractsFields() {
        RepoMetadataSnapshot meta = client.fetchRepoMetadata("org", "repo");
        assertEquals("org/repo", meta.repo());
        assertEquals(100, meta.starCount());
        assertEquals(2, meta.contributorCount());
        assertNotNull(meta.createdAt());
        assertNotNull(meta.lastPushedAt());
    }

    @Test
    void fetchHistory_skipsOpenPrs() {
        var openPrApi = new GitHubPullRequestApi() {
            @Override
            public List<Map<String, Object>> listPullRequests(String o, String r, String h, String b, String s) {return List.of();}

            @Override
            public Map<String, Object> createPullRequest(String o, String r, Map<String, Object> body)          {return Map.of();}

            @Override
            public List<Map<String, Object>> listPullRequestsByAuthor(String o, String r, String creator, String state, String sort, String direction, int perPage, int page) {
                if (page > 1) {return List.of();}
                return List.of(
                        Map.of("state", "open", "created_at", "2026-08-01T00:00:00Z", "user", Map.of("login", "alice", "id", 123)),
                        Map.of("state", "closed", "merged_at", "2026-07-01T00:00:00Z", "created_at", "2026-07-01T00:00:00Z", "user", Map.of("login", "alice", "id", 123))
                              );
            }
        };
        var c        = new GitHubContributorHistoryClient(openPrApi, new StubRepoApi());
        var snapshot = c.fetchHistory("alice", "org/repo", Instant.MIN);
        assertEquals(1, snapshot.mergedCount());
        assertEquals(0, snapshot.closedCount());
    }


    static class StubPullRequestApi implements GitHubPullRequestApi {
        @Override
        public List<Map<String, Object>> listPullRequests(String o, String r, String h, String b, String s) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listPullRequestsByAuthor(String o, String r, String creator,
                String state, String sort, String direction, int perPage, int page) {
            if (page > 1) return List.of();
            return List.of(
                Map.of("state", "closed", "merged_at", "2026-03-01T00:00:00Z",
                       "created_at", "2026-03-01T00:00:00Z",
                       "user", Map.of("login", "alice", "id", 123)),
                Map.of("state", "closed", "merged_at", "2026-02-01T00:00:00Z",
                       "created_at", "2026-01-15T00:00:00Z",
                       "user", Map.of("login", "alice", "id", 123)),
                Map.of("state", "closed",
                       "created_at", "2025-12-30T00:00:00Z",
                       "user", Map.of("login", "alice", "id", 123))
            );
        }

        @Override
        public Map<String, Object> createPullRequest(String o, String r, Map<String, Object> body) {
            return Map.of();
        }
    }

    static class StubRepoApi implements GitHubRepoApi {
        @Override
        public Map<String, Object> getRepository(String o, String r) {
            return Map.of("full_name", "org/repo", "stargazers_count", 100,
                          "created_at", "2020-01-01T00:00:00Z",
                          "pushed_at", "2026-09-01T00:00:00Z");
        }

        @Override
        public List<Map<String, Object>> listContributors(String o, String r, int perPage, String anon) {
            return List.of(Map.of("login", "alice"), Map.of("login", "bob"));
        }
    }
}
