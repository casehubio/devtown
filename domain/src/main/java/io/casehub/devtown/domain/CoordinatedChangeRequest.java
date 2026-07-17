package io.casehub.devtown.domain;

import java.util.List;

public record CoordinatedChangeRequest(List<RepoChangeEntry> repos) {}
