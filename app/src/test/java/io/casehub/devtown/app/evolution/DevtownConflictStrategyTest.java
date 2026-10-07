package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy.ConflictResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevtownConflictStrategyTest {

  private final DevtownConflictStrategy strategy = new DevtownConflictStrategy();

  @Test
  void domainId_returns_devtown() {
    assertThat(strategy.domainId()).isEqualTo("devtown");
  }

  @Test
  void clear_when_no_active_improvements() {
    var request = new ImprovementRequest(
        "recalibrate", "reviewer-calibration", "reviewer:agent-sec", 3, Map.of(), "devtown");
    var result = strategy.check(request, Map.of(), 5);
    assertThat(result).isInstanceOf(ConflictResult.Clear.class);
  }

  @Test
  void conflicting_when_same_category_and_domain_active() {
    UUID activeId = UUID.randomUUID();
    var active = new ImprovementRequest(
        "recalibrate-existing", "reviewer-calibration", "reviewer:agent-perf",
        2, Map.of(), "devtown");
    var request = new ImprovementRequest(
        "recalibrate-new", "reviewer-calibration", "reviewer:agent-sec",
        3, Map.of(), "devtown");
    var result = strategy.check(request, Map.of(activeId, active), 5);
    assertThat(result).isInstanceOf(ConflictResult.Conflicting.class);
    var conflicting = (ConflictResult.Conflicting) result;
    assertThat(conflicting.blockingImprovementId()).isEqualTo(activeId);
    assertThat(conflicting.reason()).contains("reviewer-calibration");
  }

  @Test
  void clear_when_different_category_active() {
    var active = new ImprovementRequest(
        "tune-sla", "sla-tuning", "sla:security-review", 2, Map.of(), "devtown");
    var request = new ImprovementRequest(
        "recalibrate", "reviewer-calibration", "reviewer:agent-sec", 3, Map.of(), "devtown");
    var result = strategy.check(request, Map.of(UUID.randomUUID(), active), 5);
    assertThat(result).isInstanceOf(ConflictResult.Clear.class);
  }

  @Test
  void clear_when_same_category_but_different_domain() {
    var active = new ImprovementRequest(
        "other-calibrate", "reviewer-calibration", "reviewer:other",
        2, Map.of(), "other-domain");
    var request = new ImprovementRequest(
        "recalibrate", "reviewer-calibration", "reviewer:agent-sec", 3, Map.of(), "devtown");
    var result = strategy.check(request, Map.of(UUID.randomUUID(), active), 5);
    assertThat(result).isInstanceOf(ConflictResult.Clear.class);
  }

  @Test
  void conflicting_returns_first_matching_active() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    var first = new ImprovementRequest(
        "tune-sla-1", "sla-tuning", "sla:review", 2, Map.of(), "devtown");
    var second = new ImprovementRequest(
        "tune-sla-2", "sla-tuning", "sla:ci", 2, Map.of(), "devtown");
    var request = new ImprovementRequest(
        "tune-sla-3", "sla-tuning", "sla:merge", 3, Map.of(), "devtown");
    var actives = new java.util.LinkedHashMap<UUID, ImprovementRequest>();
    actives.put(firstId, first);
    actives.put(secondId, second);
    var result = strategy.check(request, actives, 5);
    assertThat(result).isInstanceOf(ConflictResult.Conflicting.class);
    var conflicting = (ConflictResult.Conflicting) result;
    assertThat(conflicting.blockingImprovementId()).isEqualTo(firstId);
  }
}
