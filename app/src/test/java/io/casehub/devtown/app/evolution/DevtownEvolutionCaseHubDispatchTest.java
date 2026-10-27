package io.casehub.devtown.app.evolution;

import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevtownEvolutionCaseHubDispatchTest {

  private DevtownEvolutionCaseHub caseHub;
  private WorkerScope scope;

  @BeforeEach
  void setUp() {
    caseHub = new DevtownEvolutionCaseHub();
    scope = mock(WorkerScope.class);
    when(scope.caseId()).thenReturn(UUID.randomUUID());
    when(scope.accumulatedState()).thenReturn(Map.of("tenancyId", "casehubio"));
  }

  @Test
  void unknownCategory_fails() {
    var result = caseHub.dispatch(Map.of("category", "nonexistent-category"), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }

  @Test
  void missingCategory_fails() {
    var result = caseHub.dispatch(Map.of(), scope);
    assertInstanceOf(WorkerOutcome.Failed.class, result.outcome());
  }
}
