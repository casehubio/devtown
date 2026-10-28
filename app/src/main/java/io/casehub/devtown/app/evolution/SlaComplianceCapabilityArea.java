package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SlaComplianceCapabilityArea extends EventRatioCapabilityArea {

    public SlaComplianceCapabilityArea(EventLogRepository eventLog) {
        super(eventLog);
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
}
