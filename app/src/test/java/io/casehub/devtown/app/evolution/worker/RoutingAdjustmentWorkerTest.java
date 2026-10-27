package io.casehub.devtown.app.evolution.worker;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.devtown.app.evolution.ReviewQualityCapabilityArea;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.devtown.domain.evolution.ProportionalAdjustmentStrategy;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RoutingAdjustmentWorkerTest {

  private RoutingAdjustmentWorker worker;
  private PreferenceWriter preferenceWriter;
  private ReviewQualityCapabilityArea capabilityArea;
  private WorkerScope scope;

  @BeforeEach
  void setUp() {
    preferenceWriter = mock(PreferenceWriter.class);
    capabilityArea = mock(ReviewQualityCapabilityArea.class);
    worker = new RoutingAdjustmentWorker(preferenceWriter, capabilityArea,
        new ProportionalAdjustmentStrategy());
    scope = mock(WorkerScope.class);
    when(scope.caseId()).thenReturn(UUID.randomUUID());
    when(scope.accumulatedState()).thenReturn(Map.of("tenancyId", "casehubio"));
  }

  private CapabilityAreaAssessment assessment(double healthScore) {
    return new CapabilityAreaAssessment("review-quality", healthScore,
        CapabilityAreaAssessment.LandscapePosition.AT_PARITY, 0.5, 0.3, 1.67, Instant.now());
  }

  @Test
  void adjustsBlendFactorWhenHealthIsLow() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.42));
    var result = worker.apply(
        Map.of("category", "routing-adjustment", "target", "security-review"), scope);
    assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
    assertEquals("APPLIED", result.output().get("status"));
    verify(preferenceWriter, atLeastOnce()).write(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void declinesWhenHealthAboveTarget() {
    when(capabilityArea.assess(any(), anyString())).thenReturn(assessment(0.72));
    var result = worker.apply(
        Map.of("category", "routing-adjustment", "target", "security-review"), scope);
    assertInstanceOf(WorkerOutcome.Declined.class, result.outcome());
  }

  @Test
  void missingTenancyId_fails() {
    when(scope.accumulatedState()).thenReturn(Map.of());
    var result = worker.apply(
        Map.of("category", "routing-adjustment", "target", "security-review"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }

  @Test
  void missingTarget_fails() {
    var result = worker.apply(Map.of("category", "routing-adjustment"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }
}
