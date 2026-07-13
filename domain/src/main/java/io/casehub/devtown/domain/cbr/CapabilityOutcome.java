package io.casehub.devtown.domain.cbr;

import java.util.Set;

public record CapabilityOutcome(String outcome, String detail) {

    private static final Set<String> SAFE_DETAILS = Set.of("approved", "passed");

    public boolean hadFindings() {
        return "COMPLETED".equals(outcome) &&
               (detail == null || !SAFE_DETAILS.contains(detail.toLowerCase()));
    }
}
