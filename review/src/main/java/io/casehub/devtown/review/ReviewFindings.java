package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import java.util.List;

public record ReviewFindings(List<ReviewFinding> findings) {
    public ReviewFindings {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
