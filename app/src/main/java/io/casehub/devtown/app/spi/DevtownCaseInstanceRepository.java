package io.casehub.devtown.app.spi;

import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DevtownCaseInstanceRepository extends InMemoryCaseInstanceRepository {

    @Inject
    public DevtownCaseInstanceRepository(EventLogRepository eventLogRepository) {
        super(eventLogRepository);
    }
}
