package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.spi.EventLogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReviewQualityCapabilityAreaTest {

  private EventLogRepository eventLog;
  private ReviewQualityCapabilityArea area;
  private final UUID caseId = UUID.randomUUID();
  private final String tenancyId = "test-tenant";

  @BeforeEach
  void setUp() {
    eventLog = Mockito.mock(EventLogRepository.class);
    area = new ReviewQualityCapabilityArea(eventLog);
  }

  @Test
  void id_returns_review_quality() {
    assertThat(area.id()).isEqualTo("review-quality");
  }

  @Test
  void assess_returns_neutral_when_no_events() {
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(List.of());
    assertThat(area.assess(caseId, tenancyId).healthScore()).isEqualTo(0.5);
  }

  @Test
  void assess_computes_ratio_from_events() {
    var events = new ArrayList<EventLog>();
    events.addAll(createEvents(CaseHubEventType.CASE_COMPLETED, 8));
    events.addAll(createEvents(CaseHubEventType.CASE_FAULTED, 2));
    Mockito.when(eventLog.findByCaseAndTypes(
            Mockito.eq(caseId), Mockito.anyCollection(), Mockito.eq(tenancyId)))
        .thenReturn(events);
    assertThat(area.assess(caseId, tenancyId).healthScore()).isEqualTo(0.8);
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
