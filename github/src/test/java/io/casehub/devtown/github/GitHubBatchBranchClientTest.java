package io.casehub.devtown.github;

import io.casehub.devtown.domain.BatchBranchOutcome;
import io.casehub.devtown.domain.BranchDeleteOutcome;
import io.casehub.devtown.domain.PrRef;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitHubBatchBranchClientTest {

    private GitHubGitApi api;
    private GitHubBatchBranchClient client;

    @BeforeEach
    void setUp() {
        api = mock(GitHubGitApi.class);
        client = new GitHubBatchBranchClient(api);
    }

    @Nested
    class CreateBatchBranch {

        @Test
        void happyPath_allPrsMerge_returnsCreated() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.getRef("own", "repo", "heads/merge-queue/batch-b1"))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "tip-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1"), new PrRef(2, "sha-2")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.Created.class);
            var created = (BatchBranchOutcome.Created) result;
            assertThat(created.branchName()).isEqualTo("merge-queue/batch-b1");
            assertThat(created.tipSha()).isEqualTo("tip-sha");

            verify(api, times(2)).merge(eq("own"), eq("repo"), anyMap());
        }

        @Test
        void conflictOnSecondPr_returnsMergeConflict() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"))
                .thenThrow(new WebApplicationException(Response.status(409).build()));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1"), new PrRef(2, "sha-2")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.MergeConflict.class);
            var conflict = (BatchBranchOutcome.MergeConflict) result;
            assertThat(conflict.conflictPrNumber()).isEqualTo(2);
            assertThat(conflict.branchName()).isEqualTo("merge-queue/batch-b1");
        }

        @Test
        void conflictOnFirstPr_returnsMergeConflict() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenThrow(new WebApplicationException(Response.status(409).build()));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.MergeConflict.class);
            assertThat(((BatchBranchOutcome.MergeConflict) result).conflictPrNumber()).isEqualTo(1);
        }

        @Test
        void targetBranchNullSha_returnsFailure() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", null));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.Failure.class);
            assertThat(((BatchBranchOutcome.Failure) result).reason()).contains("target branch");
        }

        @Test
        void apiErrorOnMerge_returnsFailure() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenThrow(new WebApplicationException(Response.status(500).build()));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.Failure.class);
            assertThat(((BatchBranchOutcome.Failure) result).reason()).contains("HTTP 500");
        }

        @Test
        void staleBranchDeletedBeforeCreate() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.getRef("own", "repo", "heads/merge-queue/batch-b1"))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "tip-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"));

            client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            verify(api).deleteRef("own", "repo", "heads/merge-queue/batch-b1");
        }

        @Test
        void staleBranchDeleteNotFound_continuesNormally() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.getRef("own", "repo", "heads/merge-queue/batch-b1"))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "tip-sha")));
            doThrow(new WebApplicationException(Response.status(422).build()))
                .when(api).deleteRef("own", "repo", "heads/merge-queue/batch-b1");
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.Created.class);
        }

        @Test
        void tipShaNullAfterMerges_returnsFailure() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.getRef("own", "repo", "heads/merge-queue/batch-b1"))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", null));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"));

            var result = client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(1, "sha-1")));

            assertThat(result).isInstanceOf(BatchBranchOutcome.Failure.class);
            assertThat(((BatchBranchOutcome.Failure) result).reason()).contains("no resolvable SHA after merges");
        }

        @Test
        @SuppressWarnings("unchecked")
        void mergeCommitMessageIncludesPrNumber() {
            when(api.getRef("own", "repo", "heads/main"))
                .thenReturn(new GitRef("refs/heads/main", Map.of("sha", "base-sha")));
            when(api.getRef("own", "repo", "heads/merge-queue/batch-b1"))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "tip-sha")));
            when(api.createRef(eq("own"), eq("repo"), anyMap()))
                .thenReturn(new GitRef("refs/heads/merge-queue/batch-b1", Map.of("sha", "base-sha")));
            when(api.merge(eq("own"), eq("repo"), anyMap()))
                .thenReturn(Map.of("sha", "merge-sha"));

            client.createBatchBranch("own", "repo", "main", "b1",
                List.of(new PrRef(42, "sha-42")));

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(api).merge(eq("own"), eq("repo"), captor.capture());
            assertThat(captor.getValue().get("commit_message")).contains("PR #42");
        }
    }

    @Nested
    class DeleteBatchBranch {

        @Test
        void happyPath_returnsDeleted() {
            doNothing().when(api).deleteRef("own", "repo", "heads/my-branch");
            var result = client.deleteBatchBranch("own", "repo", "my-branch");
            assertThat(result).isInstanceOf(BranchDeleteOutcome.Deleted.class);
            assertThat(((BranchDeleteOutcome.Deleted) result).branchName()).isEqualTo("my-branch");
        }

        @Test
        void notFound422_returnsNotFound() {
            doThrow(new WebApplicationException(Response.status(422).build()))
                .when(api).deleteRef("own", "repo", "heads/my-branch");
            var result = client.deleteBatchBranch("own", "repo", "my-branch");
            assertThat(result).isInstanceOf(BranchDeleteOutcome.NotFound.class);
        }

        @Test
        void apiError_returnsFailure() {
            doThrow(new WebApplicationException(Response.status(500).build()))
                .when(api).deleteRef("own", "repo", "heads/my-branch");
            var result = client.deleteBatchBranch("own", "repo", "my-branch");
            assertThat(result).isInstanceOf(BranchDeleteOutcome.Failure.class);
            assertThat(((BranchDeleteOutcome.Failure) result).reason()).contains("HTTP 500");
        }
    }
}
