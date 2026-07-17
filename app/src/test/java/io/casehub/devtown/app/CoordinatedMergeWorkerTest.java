package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.casehub.devtown.domain.MergeClient;
import io.casehub.devtown.domain.MergeOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class CoordinatedMergeWorkerTest {

    CoordinatedChangeCaseHub hub;
    MergeClient mergeClient;

    @BeforeEach
    void setUp() {
        mergeClient = mock(MergeClient.class);
        hub = new CoordinatedChangeCaseHub();
        hub.mergeClient = mergeClient;
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergesAllReposSequentially() {
        when(mergeClient.merge("casehubio", "engine", 42, "abc123"))
            .thenReturn(new MergeOutcome.Success("sha1"));
        when(mergeClient.merge("casehubio", "platform", 99, "def456"))
            .thenReturn(new MergeOutcome.Success("sha2"));

        var input = Map.<String, Object>of("repos", List.of(
            Map.of("owner", "casehubio", "repo", "engine", "prNumber", 42, "headSha", "abc123"),
            Map.of("owner", "casehubio", "repo", "platform", "prNumber", 99, "headSha", "def456")
        ));

        var result = hub.adaptCoordinatedMerge(input);
        assertThat(result.outcome()).isInstanceOf(io.casehub.worker.api.WorkerOutcome.Success.class);

        var mergeResults = (List<Map<String, Object>>) result.output().get("mergeResults");
        assertThat(mergeResults).hasSize(2);
        assertThat(mergeResults.get(0).get("status")).isEqualTo("success");
        assertThat(mergeResults.get(0).get("mergeSha")).isEqualTo("sha1");
        assertThat(mergeResults.get(1).get("status")).isEqualTo("success");
        assertThat(mergeResults.get(1).get("mergeSha")).isEqualTo("sha2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsOnFirstFailure() {
        when(mergeClient.merge("casehubio", "engine", 42, "abc123"))
            .thenReturn(new MergeOutcome.Success("sha1"));
        when(mergeClient.merge("casehubio", "platform", 99, "def456"))
            .thenReturn(new MergeOutcome.Failure("merge conflict"));

        var input = Map.<String, Object>of("repos", List.of(
            Map.of("owner", "casehubio", "repo", "engine", "prNumber", 42, "headSha", "abc123"),
            Map.of("owner", "casehubio", "repo", "platform", "prNumber", 99, "headSha", "def456"),
            Map.of("owner", "casehubio", "repo", "work", "prNumber", 7, "headSha", "ghi789")
        ));

        var result = hub.adaptCoordinatedMerge(input);
        assertThat(result.outcome()).isInstanceOf(io.casehub.worker.api.WorkerOutcome.Success.class);

        var mergeResults = (List<Map<String, Object>>) result.output().get("mergeResults");
        assertThat(mergeResults).hasSize(2);
        assertThat(mergeResults.get(0).get("status")).isEqualTo("success");
        assertThat(mergeResults.get(1).get("status")).isEqualTo("failed");
        assertThat(mergeResults.get(1).get("reason")).isEqualTo("merge conflict");

        verify(mergeClient, never()).merge("casehubio", "work", 7, "ghi789");
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleRepoSuccess() {
        when(mergeClient.merge("casehubio", "engine", 42, "abc123"))
            .thenReturn(new MergeOutcome.Success("sha1"));

        var input = Map.<String, Object>of("repos", List.of(
            Map.of("owner", "casehubio", "repo", "engine", "prNumber", 42, "headSha", "abc123")
        ));

        var result = hub.adaptCoordinatedMerge(input);
        assertThat(result.outcome()).isInstanceOf(io.casehub.worker.api.WorkerOutcome.Success.class);

        var mergeResults = (List<Map<String, Object>>) result.output().get("mergeResults");
        assertThat(mergeResults).hasSize(1);
        assertThat(mergeResults.get(0).get("status")).isEqualTo("success");
    }
}
