package io.casehub.devtown.review;

import java.util.List;

public record PrDiff(
    String repo,
    int prNumber,
    String baseSha,
    String headSha,
    List<FileDiff> files,
    boolean truncated
) {
    public record FileDiff(
        String path,
        String status,
        String patch,
        int additions,
        int deletions
    ) {
        public boolean hasReviewablePatch() {
            return patch != null && !patch.isBlank();
        }
    }
}
