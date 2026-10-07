package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment.LandscapePosition;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.spi.EventLogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CiReliabilityCapabilityAreaTest {

  private EventLogRepository eventLog;
  private CiReliabilityCapabilityArea area;
  private final UUID caseId = UUID.randomUUID();
  private final String tenancyId = "test-tenant";

  @BeforeEach
  void setUp() {
    eventLog = Mockito.mock(EventLogRepository.class);
    area = new CiReliabilityCapabilityArea(eventLog);
  }

  @Test
  void id_returns_ci_reliability() {
    assertThat(area.id()).isEqualTo("ci-reliability");
  }

  @Test
  void name_returns_CI_Reliability() {
    assertThat(area.name()).isEqualTo("CI Reliability");
  }

  @Test
  void assess_returns_neutral_when_no_events() {
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(List.of());
    CapabilityAreaAssessment result = area.assess(caseId, tenancyId);
    assertThat(result.healthScore()).isEqualTo(0.5);
    assertThat(result.landscapePosition()).isEqualTo(LandscapePosition.ABSENT);
  }

  @Test
  void assess_returns_high_score_when_all_builds_pass() {
    var events = createEvents(CaseHubEventType.CASE_COMPLETED, 10);
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(events);
    CapabilityAreaAssessment result = area.assess(caseId, tenancyId);
    assertThat(result.healthScore()).isEqualTo(1.0);
    assertThat(result.landscapePosition()).isEqualTo(LandscapePosition.AHEAD);
  }

  @Test
  void assess_returns_low_score_when_many_failures() {
    var events = new ArrayList<EventLog>();
    events.addAll(createEvents(CaseHubEventType.CASE_COMPLETED, 2));
    events.addAll(createEvents(CaseHubEventType.CASE_FAULTED, 8));
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(events);
    CapabilityAreaAssessment result = area.assess(caseId, tenancyId);
    assertThat(result.healthScore()).isEqualTo(0.2);
    assertThat(result.landscapePosition()).isEqualTo(LandscapePosition.BEHIND);
  }

  @Test
  void assess_returns_mid_score_for_mixed_results() {
    var events = new ArrayList<EventLog>();
    events.addAll(createEvents(CaseHubEventType.CASE_COMPLETED, 7));
    events.addAll(createEvents(CaseHubEventType.CASE_FAULTED, 3));
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(events);
    CapabilityAreaAssessment result = area.assess(caseId, tenancyId);
    assertThat(result.healthScore()).isEqualTo(0.7);
    assertThat(result.landscapePosition()).isEqualTo(LandscapePosition.AHEAD);
  }

  private List<EventLog> createEvents(CaseHubEventType type, int count) {
    List<EventLog> events = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      EventLog e = new EventLog();
      e.setCaseId(caseId);
      e.setEventType(type);
      events.add(e);
    }
    return events;
  }
}
