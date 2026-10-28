package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReviewerTrustCapabilityArea extends EventRatioCapabilityArea {

    protected ReviewerTrustCapabilityArea() {}

    public ReviewerTrustCapabilityArea(EventLogRepository eventLog) {
        super(eventLog);
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
}
