package io.casehub.devtown.app.evolution;

import jakarta.annotation.Nullable;

public record WatchPatternRequest(
    String category,
    String areaId,
    String targetPattern,
    @Nullable Integer minEstimatedSize) {}
