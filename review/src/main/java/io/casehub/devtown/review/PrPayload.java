package io.casehub.devtown.review;

import java.util.List;

public record PrPayload(
    String repo,
    int prNumber,
    String headSha,
    String baseRef,
    int linesChanged,
    String contributor,
    List<String> changedPaths
) {}
