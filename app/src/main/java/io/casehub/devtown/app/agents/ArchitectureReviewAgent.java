package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ArchitectureReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.ARCHITECTURE_REVIEW;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        if (context.diff() == null) {return new ReviewerOutcome.Declined("no diff");}

        var files      = context.diff().files();
        int totalLines = files.stream().mapToInt(f -> f.additions() + f.deletions()).sum();
        if (totalLines < 100) {return new ReviewerOutcome.Declined("change too small for architecture review");}

        java.util.Set<String>                                   modules      = new java.util.HashSet<>();
        boolean                                                 hasPomChange = false;
        boolean                                                 hasNewModule = false;
        java.util.List<io.casehub.devtown.domain.ReviewFinding> findings     = new java.util.ArrayList<>();

        for (io.casehub.devtown.review.PrDiff.FileDiff file : files) {
            String[] parts = file.path().split("/");
            if (parts.length >= 2) {modules.add(parts[0] + "/" + parts[1]);}
            if (file.path().endsWith("pom.xml")) {hasPomChange = true;}
            if ("added".equals(file.status()) && file.path().endsWith("pom.xml")) {hasNewModule = true;}
        }

        if (modules.size() >= 3) {
            findings.add(new io.casehub.devtown.domain.ReviewFinding(
                    io.casehub.devtown.domain.ReviewFinding.Severity.MEDIUM, "cross-module",
                    files.get(0).path(), null,
                    String.format("Change spans %d modules — verify coupling", modules.size()), 0.80));
        }
        if (hasNewModule) {
            String pomPath = files.stream()
                                  .filter(f -> "added".equals(f.status()) && f.path().endsWith("pom.xml"))
                                  .findFirst().map(io.casehub.devtown.review.PrDiff.FileDiff::path).orElse("pom.xml");
            findings.add(new io.casehub.devtown.domain.ReviewFinding(
                    io.casehub.devtown.domain.ReviewFinding.Severity.HIGH, "new-module",
                    pomPath, null,
                    "New module introduced — verify dependency direction and boundary", 0.90));
        }
        if (hasPomChange && !hasNewModule) {
            findings.add(new io.casehub.devtown.domain.ReviewFinding(
                    io.casehub.devtown.domain.ReviewFinding.Severity.LOW, "dependency-change",
                    "pom.xml", null, "Build configuration changed — verify dependency scope", 0.65));
        }

        return findings.isEmpty()
               ? new ReviewerOutcome.Declined("no architecture-relevant changes")
               : new ReviewerOutcome.Completed(findings);
    }
}
