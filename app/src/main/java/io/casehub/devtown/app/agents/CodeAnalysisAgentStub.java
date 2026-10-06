package io.casehub.devtown.app.agents;

import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.ReviewContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CodeAnalysisAgentStub implements CodeAnalysisAgent {

    @Override
    public CodeAnalysisResult analyse(ReviewContext context) {
        PrDiff diff = context.diff();
        if (diff == null) {
            return new CodeAnalysisResult(true, false, false, "unknown", List.of(), List.of());
        }

        var                    securityPatterns = java.util.Set.of("auth/", "security", "session/", "token/", "rbac");
        java.util.List<String> flaggedFiles     = new java.util.ArrayList<>();
        java.util.Set<String>  modules          = new java.util.HashSet<>();
        int                    totalAdditions   = 0;
        int                    totalDeletions   = 0;

        for (PrDiff.FileDiff file : diff.files()) {
            String pathLower = file.path().toLowerCase();
            totalAdditions += file.additions();
            totalDeletions += file.deletions();

            if (securityPatterns.stream().anyMatch(pathLower::contains)) {
                flaggedFiles.add(file.path());
            }

            String[] parts = file.path().split("/");
            if (parts.length >= 2) {
                modules.add(parts[0] + "/" + parts[1]);
            }
        }

        boolean securitySensitive    = !flaggedFiles.isEmpty();
        boolean architectureCrossing = modules.size() >= 3;
        int     totalLines           = totalAdditions + totalDeletions;
        String  scope                = totalLines < 100 ? "small" : totalLines < 500 ? "medium" : "large";

        java.util.List<String> crossingPoints = architectureCrossing
                                                ? modules.stream().sorted().toList()
                                                : List.of();

        return new CodeAnalysisResult(true, securitySensitive, architectureCrossing,
                                      scope, flaggedFiles, crossingPoints);
    }
}
