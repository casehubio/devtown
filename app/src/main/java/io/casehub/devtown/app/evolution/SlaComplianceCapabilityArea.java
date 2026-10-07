package io.casehub.devtown.app.evolution;

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.runtime.improvement.area.AbstractCapabilityArea;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SlaComplianceCapabilityArea extends AbstractCapabilityArea {

  private static final Collection<CaseHubEventType> SLA_TYPES =
      List.of(CaseHubEventType.CASE_COMPLETED, CaseHubEventType.CASE_FAULTED);

  private final EventLogRepository eventLog;

  public SlaComplianceCapabilityArea(EventLogRepository eventLog) {
    this.eventLog = eventLog;
  }

  @Override
  public String id() {
    return "sla-compliance";
  }

  @Override
  public String name() {
    return "SLA Compliance";
  }

  @Override
  public String description() {
    return "SLA adherence — ratio of work items completed within SLA to total completed";
  }

  @Override
  public CapabilityAreaAssessment assess(UUID caseId, String tenancyId) {
    var events = eventLog.findByCaseAndTypes(caseId, SLA_TYPES, tenancyId);
    if (events.isEmpty()) {
      return neutralAssessment();
    }
    long successes =
        events.stream().filter(e -> e.getEventType() == CaseHubEventType.CASE_COMPLETED).count();
    double healthScore = (double) successes / events.size();
    return buildAssessment(healthScore);
  }
}
