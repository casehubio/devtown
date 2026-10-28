package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReviewQualityCapabilityArea extends EventRatioCapabilityArea {

    public ReviewQualityCapabilityArea(EventLogRepository eventLog) {
        super(eventLog);
    }

    @Override
    public String id() {
        return "review-quality";
    }

    @Override
    public String name() {
        return "Review Quality";
    }

    @Override
    public String description() {
        return "Review accuracy — ratio of accepted findings to total findings produced";
    }
}
