package io.casehub.devtown.app.spi;

import io.casehub.persistence.memory.InMemoryReactiveEventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownReactiveEventLogRepository extends InMemoryReactiveEventLogRepository {}
