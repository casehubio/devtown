package io.casehub.devtown.domain.sla;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SlaEstimateTest {

    @Test
    void durationStats_toMap_producesCorrectKeys() {
        var stats = new DurationStats(Duration.ofMinutes(12), Duration.ofMinutes(3),
                                      Duration.ofMinutes(25), 5);
        var map = stats.toMap();
        assertThat(map.get("medianSeconds")).isEqualTo(720L);
        assertThat(map.get("minSeconds")).isEqualTo(180L);
        assertThat(map.get("maxSeconds")).isEqualTo(1500L);
        assertThat(map.get("sampleCount")).isEqualTo(5);
        assertThat(map.get("precedentCount")).isEqualTo(5);
    }

    @Test
    void toContextMap_includesOverallAndBreakdown() {
        var overall = new DurationStats(Duration.ofMinutes(12), Duration.ofMinutes(3),
                                        Duration.ofMinutes(25), 5);
        var breakdown = Map.of(
                "code-analysis", new DurationStats(Duration.ofMinutes(4), Duration.ofMinutes(2),
                                                   Duration.ofMinutes(8), 4),
                "security-review", new DurationStats(Duration.ofMinutes(10), Duration.ofMinutes(5),
                                                     Duration.ofMinutes(20), 3));
        var estimate = new SlaEstimate(overall, breakdown);
        var map      = estimate.toContextMap();

        assertThat(map.get("medianSeconds")).isEqualTo(720L);
        assertThat(map.get("precedentCount")).isEqualTo(5);
        assertThat(map.get("sampleCount")).isEqualTo(5);

        @SuppressWarnings("unchecked")
        var capBreakdown = (Map<String, Map<String, Object>>) map.get("capabilityBreakdown");
        assertThat(capBreakdown).containsKeys("code-analysis", "security-review");
        assertThat(capBreakdown.get("code-analysis").get("medianSeconds")).isEqualTo(240L);
        assertThat(capBreakdown.get("security-review").get("medianSeconds")).isEqualTo(600L);
    }

    @Test
    void toContextMap_emptyBreakdown_noBreakdownKey() {
        var overall = new DurationStats(Duration.ofMinutes(12), Duration.ofMinutes(3),
                                        Duration.ofMinutes(25), 5);
        var estimate = new SlaEstimate(overall, Map.of());
        var map      = estimate.toContextMap();

        assertThat(map).doesNotContainKey("capabilityBreakdown");
        assertThat(map.get("medianSeconds")).isEqualTo(720L);
    }

    @Test
    void subSecondDurationsTruncateToZero() {
        var overall = new DurationStats(Duration.ofMillis(500), Duration.ofMillis(500),
                                        Duration.ofMillis(500), 1);
        var map = new SlaEstimate(overall, Map.of()).toContextMap();
        assertThat(map.get("medianSeconds")).isEqualTo(0L);
    }
}
