package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MergeQueueHealthCapabilityArea extends EventRatioCapabilityArea {

    protected MergeQueueHealthCapabilityArea() {}

    public MergeQueueHealthCapabilityArea(EventLogRepository eventLog) {
        super(eventLog);
    }

    @Override
    public String id() {
        return "merge-queue-health";
    }

    @Override
    public String name() {
        return "Merge Queue Health";
    }

    @Override
    public String description() {
        return "Merge throughput — ratio of successful merges to total merge attempts";
    }
}
