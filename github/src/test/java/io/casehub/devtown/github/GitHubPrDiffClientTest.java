package io.casehub.devtown.github;

import io.casehub.devtown.review.PrDiff;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubPrDiffClientTest {

    @Test
    void fetchDiff_mapsFilesToPrDiff() {
        var api = Mockito.mock(GitHubPullRequestApi.class);
        when(api.listPullRequestFiles("org", "repo", 42, 100, 1))
            .thenReturn(List.of(
                Map.of("filename", "src/Main.java", "status", "modified",
                    "patch", "+ new line", "additions", 1, "deletions", 0),
                Map.of("filename", "image.png", "status", "added",
                    "additions", 0, "deletions", 0)));

        var client = new GitHubPrDiffClient(api);
        PrDiff diff = client.fetchDiff("org/repo", 42);

        assertEquals("org/repo", diff.repo());
        assertEquals(42, diff.prNumber());
        assertEquals(2, diff.files().size());

        var javaFile = diff.files().get(0);
        assertEquals("src/Main.java", javaFile.path());
        assertEquals("modified", javaFile.status());
        assertEquals("+ new line", javaFile.patch());
        assertTrue(javaFile.hasReviewablePatch());

        var imageFile = diff.files().get(1);
        assertEquals("image.png", imageFile.path());
        assertNull(imageFile.patch());
        assertFalse(imageFile.hasReviewablePatch());
    }

    @Test
    void fetchDiff_paginatesWhenFullPage() {
        var api = Mockito.mock(GitHubPullRequestApi.class);
        var page1 = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < 100; i++) {
            page1.add(Map.of("filename", "file" + i + ".java", "status", "modified",
                "patch", "+ code", "additions", 1, "deletions", 0));
        }
        when(api.listPullRequestFiles("org", "repo", 1, 100, 1)).thenReturn(page1);
        when(api.listPullRequestFiles("org", "repo", 1, 100, 2)).thenReturn(List.of(
            Map.of("filename", "file100.java", "status", "added",
                "patch", "+ more", "additions", 1, "deletions", 0)));

        var client = new GitHubPrDiffClient(api);
        PrDiff diff = client.fetchDiff("org/repo", 1);

        assertEquals(101, diff.files().size());
        assertFalse(diff.truncated());
    }

    @Test
    void fetchDiff_detectsTruncation() {
        var api = Mockito.mock(GitHubPullRequestApi.class);
        var largePage = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < 100; i++) {
            largePage.add(Map.of("filename", "f" + i + ".java", "status", "modified",
                "patch", "+", "additions", 1, "deletions", 0));
        }
        // Simulate 30 pages of 100 = 3000 files (GitHub cap)
        for (int p = 1; p <= 30; p++) {
            when(api.listPullRequestFiles("org", "repo", 1, 100, p)).thenReturn(largePage);
        }
        when(api.listPullRequestFiles("org", "repo", 1, 100, 31)).thenReturn(List.of());

        var client = new GitHubPrDiffClient(api);
        PrDiff diff = client.fetchDiff("org/repo", 1);

        assertEquals(3000, diff.files().size());
        assertTrue(diff.truncated());
    }
}
