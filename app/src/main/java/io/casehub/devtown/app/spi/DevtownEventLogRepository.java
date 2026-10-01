package io.casehub.devtown.app.spi;

import io.casehub.engine.persistence.memory.InMemoryEventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownEventLogRepository extends InMemoryEventLogRepository {}
