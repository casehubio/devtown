package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.MergeQueueHealthCapabilityArea;
import io.casehub.devtown.domain.evolution.AdjustmentBounds;
import io.casehub.devtown.domain.evolution.AdjustmentResult;
import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.casehub.devtown.app.evolution.worker.EvolutionWorkerConstants.TARGET_HEALTH;

@ApplicationScoped
public class QueuePriorityWorker {

  static final AdjustmentBounds MAX_BATCH_BOUNDS = new AdjustmentBounds(
      2, 20, 1, 3, 10.0,
      AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds MIN_BATCH_BOUNDS = new AdjustmentBounds(
      1, 5, 1, 1, 3.0,
      AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds DECAY_RATE_BOUNDS = new AdjustmentBounds(
      50, 500, 10, 50, 200.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds FAILURE_WINDOW_BOUNDS = new AdjustmentBounds(
      5, 50, 2, 10, 30.0,
      AdjustmentBounds.Direction.INCREASE_ON_LOW_HEALTH);
  static final AdjustmentBounds HIGH_RISK_BOUNDS = new AdjustmentBounds(
      0.3, 0.8, 0.02, 0.10, 0.5,
      AdjustmentBounds.Direction.DECREASE_ON_LOW_HEALTH);

  private final PreferenceWriter preferenceWriter;
  private final MergeQueueHealthCapabilityArea capabilityArea;
  private final AdjustmentStrategy strategy;

  @Inject
  public QueuePriorityWorker(
      PreferenceWriter preferenceWriter,
      MergeQueueHealthCapabilityArea capabilityArea,
      AdjustmentStrategy strategy) {
    this.preferenceWriter = preferenceWriter;
    this.capabilityArea = capabilityArea;
    this.strategy = strategy;
  }

  public WorkerResult<Map<String, Object>> apply(
      Map<String, Object> input, WorkerScope scope) {

    String tenancyId = (String) scope.accumulatedState().get("tenancyId");
    if (tenancyId == null) {
      return WorkerResult.failed("Missing 'tenancyId' in accumulated state");
    }

    CapabilityAreaAssessment assessment = capabilityArea.assess(scope.caseId(), tenancyId);
    double health = assessment.healthScore();

    if (health >= TARGET_HEALTH) {
      return WorkerResult.declined(
          String.format("Health %.2f >= target %.2f", health, TARGET_HEALTH));
    }

    String mqScope = "casehubio/devtown/merge-queue";
    String mqNs = "devtown.merge-queue";

    List<Map<String, Object>> adjustments = new ArrayList<>();

    AdjustmentResult maxAdj = strategy.compute(
        "max-batch-size", 10, health, TARGET_HEALTH, MAX_BATCH_BOUNDS,
        "queue-optimization", scope.caseId());
    AdjustmentResult minAdj = strategy.compute(
        "min-batch-size", 1, health, TARGET_HEALTH, MIN_BATCH_BOUNDS,
        "queue-optimization", scope.caseId());

    double maxBatch = Math.round(maxAdj.newValue());
    double minBatch = Math.round(minAdj.newValue());
    if (minBatch > maxBatch) {
      minBatch = maxBatch;
    }

    if (maxBatch != maxAdj.oldValue()) {
      preferenceWriter.write(mqScope, mqNs, "max-batch-size",
          String.valueOf((int) maxBatch));
      adjustments.add(new AdjustmentResult("max-batch-size", maxAdj.oldValue(),
          maxBatch, maxAdj.method(), maxAdj.precedentCount(), maxAdj.rationale()).toMap());
    }
    if (minBatch != minAdj.oldValue()) {
      preferenceWriter.write(mqScope, mqNs, "min-batch-size",
          String.valueOf((int) minBatch));
      adjustments.add(new AdjustmentResult("min-batch-size", minAdj.oldValue(),
          minBatch, minAdj.method(), minAdj.precedentCount(), minAdj.rationale()).toMap());
    }

    AdjustmentResult decayAdj = strategy.compute(
        "decay-rate-per-hour", 125, health, TARGET_HEALTH, DECAY_RATE_BOUNDS,
        "queue-optimization", scope.caseId());
    if (decayAdj.oldValue() != decayAdj.newValue()) {
      preferenceWriter.write(mqScope, mqNs + ".priority", "decay-rate-per-hour",
          String.valueOf(Math.round(decayAdj.newValue())));
      adjustments.add(decayAdj.toMap());
    }

    AdjustmentResult windowAdj = strategy.compute(
        "failure-rate-window", 20, health, TARGET_HEALTH, FAILURE_WINDOW_BOUNDS,
        "queue-optimization", scope.caseId());
    if (windowAdj.oldValue() != windowAdj.newValue()) {
      preferenceWriter.write(mqScope, mqNs, "failure-rate-window",
          String.valueOf(Math.round(windowAdj.newValue())));
      adjustments.add(windowAdj.toMap());
    }

    AdjustmentResult riskAdj = strategy.compute(
        "high-risk-threshold", 0.5, health, TARGET_HEALTH, HIGH_RISK_BOUNDS,
        "queue-optimization", scope.caseId());
    if (riskAdj.oldValue() != riskAdj.newValue()) {
      preferenceWriter.write(mqScope, mqNs, "high-risk-threshold",
          String.valueOf(riskAdj.newValue()));
      adjustments.add(riskAdj.toMap());
    }

    var output = new LinkedHashMap<String, Object>();
    output.put("category", "queue-optimization");
    output.put("target", "merge-queue-health");
    output.put("adjustments", adjustments);
    output.put("healthBefore", health);
    output.put("status", "MERGED");
    output.put("reviewOutcome", "approved");
    output.put("requiresPr", false);
    return WorkerResult.of(output);
  }
}
