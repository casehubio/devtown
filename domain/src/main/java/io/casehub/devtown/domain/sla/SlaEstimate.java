package io.casehub.devtown.domain.sla;

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
