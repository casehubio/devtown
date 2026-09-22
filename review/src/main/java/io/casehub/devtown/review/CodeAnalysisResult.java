package io.casehub.devtown.review;

import java.util.List;

public record CodeAnalysisResult(
    boolean complete,
    boolean securitySensitive,
    boolean architectureCrossing,
    String scope,
    List<String> flaggedFiles,
    List<String> crossingPoints
) {
    public CodeAnalysisResult {
        flaggedFiles = flaggedFiles == null ? List.of() : List.copyOf(flaggedFiles);
        crossingPoints = crossingPoints == null ? List.of() : List.copyOf(crossingPoints);
    }
}
