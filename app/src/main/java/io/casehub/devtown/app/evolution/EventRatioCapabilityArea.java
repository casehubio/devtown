package io.casehub.devtown.app.evolution;

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.runtime.improvement.area.AbstractCapabilityArea;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public abstract class EventRatioCapabilityArea extends AbstractCapabilityArea {

    private static final Collection<CaseHubEventType> EVENT_TYPES =
            List.of(CaseHubEventType.CASE_COMPLETED, CaseHubEventType.CASE_FAULTED);

    protected final EventLogRepository eventLog;

    protected EventRatioCapabilityArea(EventLogRepository eventLog) {
        this.eventLog = eventLog;
    }

    @Override
    public CapabilityAreaAssessment assess(UUID caseId, String tenancyId) {
        var events = eventLog.findByCaseAndTypes(caseId, EVENT_TYPES, tenancyId);
        if (events.isEmpty()) {
            return neutralAssessment();
        }
        long successes = events.stream()
                .filter(e -> e.getEventType() == CaseHubEventType.CASE_COMPLETED)
                .count();
        double healthScore = (double) successes / events.size();
        return buildAssessment(healthScore);
    }
}
