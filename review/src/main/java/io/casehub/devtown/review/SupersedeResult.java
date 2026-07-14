package io.casehub.devtown.review;

import java.util.UUID;

public record SupersedeResult(
    UUID supersededCaseId,
    UUID replacementCaseId,
    PrReviewOutcome reviewOutcome
) {
    public static SupersedeResult noActiveCase() {
        return new SupersedeResult(null, null, null);
    }

    public static SupersedeResult alreadyTerminal(UUID caseId) {
        return new SupersedeResult(caseId, null, null);
    }

    public boolean succeeded() {
        return supersededCaseId != null && replacementCaseId != null;
    }
}
