package io.casehub.devtown.domain.sla;

/**
 * Per-capability {@code sampleCount} values may exceed the overall {@code sampleCount}.
 * Overall counts precedents with valid positive {@code completionTime} (end-to-end case duration).
 * Per-capability counts outcomes with valid non-negative {@code duration} (per-capability timing).
 * A precedent can have valid per-capability durations but null total completion time, or vice versa.
 */
public record SlaEstimate(
        DurationStats overall,
        java.util.Map<String, DurationStats> capabilityBreakdown
) {
    public java.util.Map<String, Object> toContextMap() {
        var map = new java.util.LinkedHashMap<>(overall.toMap());
        if (!capabilityBreakdown.isEmpty()) {
            var breakdown = new java.util.LinkedHashMap<String, Object>();
            capabilityBreakdown.forEach((k, v) -> breakdown.put(k, v.toMap()));
            map.put("capabilityBreakdown", breakdown);
        }
        return map;
    }
}
