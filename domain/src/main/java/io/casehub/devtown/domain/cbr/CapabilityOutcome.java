package io.casehub.devtown.domain.cbr;

public record CapabilityOutcome(String outcome, String detail, java.time.Duration duration) {

    private static final java.util.Set<String> SAFE_DETAILS = java.util.Set.of("approved", "passed");

    public boolean hadFindings() {
        return "COMPLETED".equals(outcome) &&
               (detail == null || !SAFE_DETAILS.contains(detail.toLowerCase()));
    }
}
