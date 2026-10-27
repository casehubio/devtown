package io.casehub.devtown.app.evolution;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.devtown.app.evolution.worker.ReviewerCalibrationWorker;
import io.casehub.devtown.app.evolution.worker.GateStrictnessWorker;
import io.casehub.devtown.app.evolution.worker.QueuePriorityWorker;
import io.casehub.devtown.app.evolution.worker.RoutingAdjustmentWorker;
import io.casehub.devtown.app.evolution.worker.SlaThresholdWorker;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class DevtownEvolutionCaseHub extends YamlCaseHub {

  @Inject ReviewerCalibrationWorker reviewerCalibration;
  @Inject RoutingAdjustmentWorker routingAdjustment;
  @Inject SlaThresholdWorker slaThreshold;
  @Inject GateStrictnessWorker gateStrictness;
  @Inject QueuePriorityWorker queuePriority;

  public DevtownEvolutionCaseHub() {
    super("casehub/devtown/evolution.yaml");
  }

  @Override
  protected void augment(CaseDefinition definition) {
    definition.getWorkers().add(Worker.builder()
        .name("evolution-apply")
        .capabilityName("improvement-implement")
        .function(new WorkerFunction.Sync<>(Map.class, Map.class, this::dispatch))
        .build());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  WorkerResult dispatch(Map input, WorkerScope scope) {
    String category = (String) input.get("category");
    if (category == null) {
      return WorkerResult.failed("Missing 'category' in evolution apply input");
    }
    return switch (category) {
      case "reviewer-calibration" -> reviewerCalibration.apply(input, scope);
      case "routing-adjustment" -> routingAdjustment.apply(input, scope);
      case "sla-tuning" -> slaThreshold.apply(input, scope);
      case "gate-tightening" -> gateStrictness.apply(input, scope);
      case "queue-optimization" -> queuePriority.apply(input, scope);
      default -> WorkerResult.failed("Unknown evolution category: " + category);
    };
  }
}
