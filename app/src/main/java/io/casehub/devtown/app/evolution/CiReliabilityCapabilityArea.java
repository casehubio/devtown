package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CiReliabilityCapabilityArea extends EventRatioCapabilityArea {


    CiReliabilityCapabilityArea() {
    }

    public CiReliabilityCapabilityArea(EventLogRepository eventLog) {
        super(eventLog);
    }

    @Override
    public String id() {
        return "ci-reliability";
    }

    @Override
    public String name() {
        return "CI Reliability";
    }

    @Override
    public String description() {
        return "Build pass rate — ratio of successful CI runs to total runs";
    }
}
