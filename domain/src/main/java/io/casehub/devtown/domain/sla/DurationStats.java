package io.casehub.devtown.domain.sla;

import java.time.Duration;
import java.util.Map;

public record DurationStats(Duration median, Duration min, Duration max, int sampleCount) {
    public Map<String, Object> toMap() {
        return Map.of(
            "medianSeconds", median.toSeconds(),
            "minSeconds", min.toSeconds(),
            "maxSeconds", max.toSeconds(),
            "sampleCount", sampleCount,
            "precedentCount", sampleCount);
    }
}
