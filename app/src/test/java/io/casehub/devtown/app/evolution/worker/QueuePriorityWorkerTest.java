package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.MergeQueueHealthCapabilityArea;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.devtown.domain.evolution.ProportionalAdjustmentStrategy;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QueuePriorityWorkerTest {

  private QueuePriorityWorker worker;
  private PreferenceWriter preferenceWriter;
  private MergeQueueHealthCapabilityArea capabilityArea;
  private WorkerScope scope;

  @BeforeEach
  void setUp() {
    preferenceWriter = mock(PreferenceWriter.class);
    capabilityArea = mock(MergeQueueHealthCapabilityArea.class);
    worker = new QueuePriorityWorker(preferenceWriter, capabilityArea,
        new ProportionalAdjustmentStrategy());
    scope = mock(WorkerScope.class);
    when(scope.caseId()).thenReturn(UUID.randomUUID());
    when(scope.accumulatedState()).thenReturn(Map.of("tenancyId", "casehubio"));
  }

  private CapabilityAreaAssessment assessment(double healthScore) {
    return new CapabilityAreaAssessment("merge-queue-health", healthScore,
        CapabilityAreaAssessment.LandscapePosition.AT_PARITY, 0.5, 0.3, 1.67, Instant.now());
  }

  @Test
  void adjustsQueueParamsWhenHealthIsLow() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.38));
    var result = worker.apply(
        Map.of("category", "queue-optimization", "target", "merge-queue-health"), scope);
    assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
    assertEquals("MERGED", result.output().get("status"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void enforcesMinLessThanMaxBatchSize() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.20));
    var result = worker.apply(
        Map.of("category", "queue-optimization", "target", "merge-queue-health"), scope);

    List<Map<String, Object>> adjustments =
        (List<Map<String, Object>>) result.output().get("adjustments");

    double min = -1, max = -1;
    for (var adj : adjustments) {
      switch ((String) adj.get("parameter")) {
        case "min-batch-size" -> min = ((Number) adj.get("newValue")).doubleValue();
        case "max-batch-size" -> max = ((Number) adj.get("newValue")).doubleValue();
      }
    }
    if (min > 0 && max > 0) {
      assertTrue(min <= max, "MIN_BATCH_SIZE (" + min + ") must be <= MAX_BATCH_SIZE (" + max + ")");
    }
  }

  @Test
  void declinesWhenHealthAboveTarget() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.72));
    var result = worker.apply(
        Map.of("category", "queue-optimization", "target", "merge-queue-health"), scope);
    assertInstanceOf(WorkerOutcome.Declined.class, result.outcome());
  }
}
