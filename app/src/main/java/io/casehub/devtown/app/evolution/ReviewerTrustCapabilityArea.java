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
public class ReviewerTrustCapabilityArea extends AbstractCapabilityArea {

  private static final Collection<CaseHubEventType> TRUST_TYPES =
      List.of(CaseHubEventType.CASE_COMPLETED, CaseHubEventType.CASE_FAULTED);

  private final EventLogRepository eventLog;

  public ReviewerTrustCapabilityArea(EventLogRepository eventLog) {
    this.eventLog = eventLog;
  }

  @Override
  public String id() {
    return "reviewer-trust";
  }

  @Override
  public String name() {
    return "Reviewer Trust";
  }

  @Override
  public String description() {
    return "Trust calibration — correlation between trust scores and review outcomes";
  }

  @Override
  public CapabilityAreaAssessment assess(UUID caseId, String tenancyId) {
    var events = eventLog.findByCaseAndTypes(caseId, TRUST_TYPES, tenancyId);
    if (events.isEmpty()) {
      return neutralAssessment();
    }
    long successes =
        events.stream().filter(e -> e.getEventType() == CaseHubEventType.CASE_COMPLETED).count();
    double healthScore = (double) successes / events.size();
    return buildAssessment(healthScore);
  }
}
