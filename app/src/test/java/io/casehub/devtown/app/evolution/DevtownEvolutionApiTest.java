package io.casehub.devtown.app.evolution;

import io.casehub.api.model.improvement.ComplianceLevel;
import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.api.model.stigmergy.ConductorInboxEntry;
import io.casehub.api.spi.improvement.EngineEvolutionApi;
import io.casehub.api.view.EvolutionStateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevtownEvolutionApiTest {

  private EngineEvolutionApi engineApi;
  private DevtownEvolutionCaseResolver resolver;
  private DevtownEvolutionEnricher enricher;
  private DevtownEvolutionApi api;

  private final UUID caseId = UUID.randomUUID();
  private final String tenancyId = "test-tenant";

  @BeforeEach
  void setUp() {
    engineApi = Mockito.mock(EngineEvolutionApi.class);
    resolver = Mockito.mock(DevtownEvolutionCaseResolver.class);
    enricher = new DevtownEvolutionEnricher();

    api = new DevtownEvolutionApi();
    api.engineApi = engineApi;
    api.caseResolver = resolver;
    api.enricher = enricher;

    when(resolver.resolve(tenancyId)).thenReturn(caseId);
  }

  @Test
  void getEvolutionState_resolves_case_and_enriches() {
    var state = createState();
    when(engineApi.getEvolutionState(eq(caseId), eq(tenancyId), any())).thenReturn(state);

    DevtownEvolutionStateSnapshot result = api.getEvolutionState(tenancyId);

    assertThat(result.engineState()).isSameAs(state);
    verify(resolver).resolve(tenancyId);
  }

  @Test
  void getInbox_delegates_to_engine() {
    var entries = List.of(
        new ConductorInboxEntry(
            caseId, "entry-1", "propose", ConductorInboxEntry.Status.PENDING,
            "reviewer-calibration", null, UUID.randomUUID(), "Recalibrate trust",
            List.of(), 0.8, Instant.now(), null, 1440, null));
    when(engineApi.getInbox(caseId, tenancyId)).thenReturn(entries);

    List<ConductorInboxEntry> result = api.getInbox(tenancyId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("entry-1");
  }

  @Test
  void resolveGate_delegates_to_engine() {
    api.resolveGate(tenancyId, "entry-1", ConductorInboxEntry.Status.APPROVED, "looks good", null);

    verify(engineApi).resolveGate(
        caseId, tenancyId, "entry-1", ConductorInboxEntry.Status.APPROVED, "looks good", null);
  }

  @Test
  void pauseCategory_delegates_to_engine() {
    api.pauseCategory(tenancyId, "reviewer-calibration", 60);

    verify(engineApi).pauseCategory(caseId, tenancyId, "reviewer-calibration", 60);
  }

  @Test
  void resetCircuitBreaker_delegates_to_engine() {
    api.resetCircuitBreaker(tenancyId);

    verify(engineApi).resetCircuitBreaker(caseId);
  }

    @Test
    void addWatchPattern_delegates_to_engine() {
        api.addWatchPattern(tenancyId, "ci-reliability", "area-1", "*.yml", 50);

        verify(engineApi).addWatchPattern(caseId, tenancyId, "ci-reliability", "area-1", "*.yml", 50);
    }


    @Test
    void getConductorOrigin_returns_driven_when_stream_targets_case() {
        var stream = new EvolutionStateSnapshot.ImprovementStreamView(
                UUID.randomUUID(), "ci-reliability", "target-case-id", "analyze",
                List.of(), null, false, Instant.now());
        when(engineApi.getStreamProgress(caseId, tenancyId)).thenReturn(List.of(stream));

        ConductorOriginResponse result = api.getConductorOrigin(tenancyId, "target-case-id");

        assertThat(result.conductorDriven()).isTrue();
        assertThat(result.streamCategory()).isEqualTo("ci-reliability");
    }

    @Test
    void getConductorOrigin_returns_not_driven_when_no_match() {
        when(engineApi.getStreamProgress(caseId, tenancyId)).thenReturn(List.of());

        ConductorOriginResponse result = api.getConductorOrigin(tenancyId, "unknown-case-id");

        assertThat(result.conductorDriven()).isFalse();
        assertThat(result.streamCategory()).isNull();
    }


    private EvolutionStateSnapshot createState() {
    return new EvolutionStateSnapshot(
        caseId, Instant.now(), 0.75, Map.of(), 0.0, 1440,
        CircuitBreakerState.CLOSED, Map.of(), ComplianceLevel.L0_INERT, null, Map.of(),
        List.of(), 0, 0, List.of(), false, 0);
  }
}
