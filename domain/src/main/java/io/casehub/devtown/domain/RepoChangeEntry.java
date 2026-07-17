package io.casehub.devtown.domain;

import java.util.List;

public record RepoChangeEntry(
    String owner, String repo, int prNumber,
    String headSha, String targetBranch, String contributor,
    List<String> changedPaths, int linesChanged) {}
