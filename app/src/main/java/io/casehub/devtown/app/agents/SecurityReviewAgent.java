package io.casehub.devtown.app.agents;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.devtown.review.ReviewerAgent;
import io.casehub.devtown.review.ReviewerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecurityReviewAgent implements ReviewerAgent {

    @Override
    public String capability() {
        return ReviewDomain.SECURITY_REVIEW;
    }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        if (context.diff() == null) {return new ReviewerOutcome.Declined("no diff");}

        var                           securityPaths = java.util.Set.of("auth/", "session/", "token/", "security", "rbac");
        java.util.List<ReviewFinding> findings      = new java.util.ArrayList<>();

        for (io.casehub.devtown.review.PrDiff.FileDiff file : context.diff().files()) {
            String  pathLower = file.path().toLowerCase();
            boolean relevant  = securityPaths.stream().anyMatch(pathLower::contains);
            if (!relevant || !file.hasReviewablePatch()) {continue;}

            String patch = file.patch();
            if (patch.contains("password") || patch.contains("secret") || patch.contains("credential")) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.HIGH, "credential-exposure",
                                               file.path(), extractLineRange(patch), "Potential credential exposure in changed code", 0.85));
            }
            if (patch.contains("session") || patch.contains("Session")) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.MEDIUM, "session-management",
                                               file.path(), extractLineRange(patch), "Session handling modified — verify fixation protection", 0.75));
            }
            if (findings.stream().noneMatch(f -> f.filePath().equals(file.path()))) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.MEDIUM, "security-change",
                                               file.path(), extractLineRange(patch), "Security-sensitive file modified — review required", 0.70));
            }
        }

        return findings.isEmpty()
               ? new ReviewerOutcome.Declined("no security-relevant files")
               : new ReviewerOutcome.Completed(findings);
    }

    private static ReviewFinding.LineRange extractLineRange(String patch) {
        var matcher = java.util.regex.Pattern.compile("@@ -(\\d+)").matcher(patch);
        if (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            return new ReviewFinding.LineRange(start, start + 5);
        }
        return null;
    }

}
