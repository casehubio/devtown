package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.SlaComplianceCapabilityArea;
import io.casehub.devtown.domain.evolution.PreferenceReader;
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

class SlaThresholdWorkerTest {

  private SlaThresholdWorker worker;
  private PreferenceWriter preferenceWriter;
  private SlaComplianceCapabilityArea capabilityArea;
  private WorkerScope scope;

  @BeforeEach
  void setUp() {
    preferenceWriter = mock(PreferenceWriter.class);
    capabilityArea = mock(SlaComplianceCapabilityArea.class);
    PreferenceReader preferenceReader = (s, n, k) -> java.util.OptionalDouble.empty();
    worker = new SlaThresholdWorker(preferenceWriter, preferenceReader, capabilityArea,
        new ProportionalAdjustmentStrategy());
    scope = mock(WorkerScope.class);
    when(scope.caseId()).thenReturn(UUID.randomUUID());
    when(scope.accumulatedState()).thenReturn(Map.of("tenancyId", "casehubio"));
  }

  private CapabilityAreaAssessment assessment(double healthScore) {
    return new CapabilityAreaAssessment("sla-compliance", healthScore,
        CapabilityAreaAssessment.LandscapePosition.AT_PARITY, 0.5, 0.3, 1.67, Instant.now());
  }

  @Test
  void adjustsSlaWhenHealthIsLow() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.40));
    var result = worker.apply(
        Map.of("category", "sla-tuning", "target", "sla-compliance"), scope);
    assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
    assertEquals("APPLIED", result.output().get("status"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void enforcesSlaOrdering() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.30));
    var result = worker.apply(
        Map.of("category", "sla-tuning", "target", "sla-compliance"), scope);

    List<Map<String, Object>> adjustments =
        (List<Map<String, Object>>) result.output().get("adjustments");

    double critical = -1, high = -1, normal = -1;
    for (var adj : adjustments) {
      switch ((String) adj.get("parameter")) {
        case "sla.critical" -> critical = ((Number) adj.get("newValue")).doubleValue();
        case "sla.high" -> high = ((Number) adj.get("newValue")).doubleValue();
        case "sla.normal" -> normal = ((Number) adj.get("newValue")).doubleValue();
      }
    }
    if (critical > 0 && high > 0) {
      assertTrue(critical < high, "CRITICAL (" + critical + ") must be less than HIGH (" + high + ")");
    }
    if (high > 0 && normal > 0) {
      assertTrue(high < normal, "HIGH (" + high + ") must be less than NORMAL (" + normal + ")");
    }
  }

  @Test
  void declinesWhenHealthAboveTarget() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.72));
    var result = worker.apply(
        Map.of("category", "sla-tuning", "target", "sla-compliance"), scope);
    assertInstanceOf(WorkerOutcome.Declined.class, result.outcome());
  }

  @Test
  void missingTenancyId_fails() {
    when(scope.accumulatedState()).thenReturn(Map.of());
    var result = worker.apply(
        Map.of("category", "sla-tuning", "target", "sla-compliance"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }
}
