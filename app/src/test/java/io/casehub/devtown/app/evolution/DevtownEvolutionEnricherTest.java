package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.improvement.ComplianceLevel;
import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.api.view.EvolutionStateSnapshot;
import io.casehub.api.view.EvolutionStateSnapshot.ImprovementStreamView;
import io.casehub.api.view.EvolutionStateSnapshot.StageProgress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevtownEvolutionEnricherTest {

  private final DevtownEvolutionEnricher enricher = new DevtownEvolutionEnricher();

  @Test
  void enriches_reviewer_calibration_target() {
    var stream = createStream("reviewer-calibration", "reviewer:agent-security");
    var state = createState(List.of(stream));

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.enrichedStreams()).hasSize(1);
    assertThat(result.enrichedStreams().get(0).enrichedTarget())
        .isEqualTo("Reviewer agent-security");
  }

  @Test
  void enriches_sla_tuning_target() {
    var stream = createStream("sla-tuning", "sla:security-review");
    var state = createState(List.of(stream));

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.enrichedStreams().get(0).enrichedTarget())
        .isEqualTo("SLA: sla:security-review");
  }

  @Test
  void enriches_queue_optimization_target() {
    var stream = createStream("queue-optimization", "batch-size");
    var state = createState(List.of(stream));

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.enrichedStreams().get(0).enrichedTarget())
        .isEqualTo("Queue: batch-size");
  }

  @Test
  void preserves_null_target() {
    var stream = createStream("reviewer-calibration", null);
    var state = createState(List.of(stream));

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.enrichedStreams().get(0).enrichedTarget()).isNull();
  }

  @Test
  void preserves_engine_state_reference() {
    var state = createState(List.of());

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.engineState()).isSameAs(state);
  }

  @Test
  void enriches_multiple_streams() {
    var s1 = createStream("reviewer-calibration", "reviewer:agent-perf");
    var s2 = createStream("sla-tuning", "sla:code-review");
    var state = createState(List.of(s1, s2));

    DevtownEvolutionStateSnapshot result = enricher.enrich(state);

    assertThat(result.enrichedStreams()).hasSize(2);
    assertThat(result.enrichedStreams().get(0).enrichedTarget())
        .isEqualTo("Reviewer agent-perf");
    assertThat(result.enrichedStreams().get(1).enrichedTarget())
        .isEqualTo("SLA: sla:code-review");
  }

  private ImprovementStreamView createStream(String category, String target) {
    return new ImprovementStreamView(
        UUID.randomUUID(), category, target, "analyze",
        List.of(new StageProgress("analyze", StageProgress.StageStatus.IN_PROGRESS,
            Instant.now(), null)),
        null, false, Instant.now());
  }

  private EvolutionStateSnapshot createState(List<ImprovementStreamView> streams) {
    return new EvolutionStateSnapshot(
        UUID.randomUUID(), Instant.now(), 0.75, Map.of(), 0.0, 1440,
        CircuitBreakerState.CLOSED, Map.of(), ComplianceLevel.L0_INERT, null, Map.of(),
        List.of(), 0, 0, streams, false, 0);
  }
}
