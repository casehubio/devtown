package io.casehub.devtown.app.spi;

import io.casehub.persistence.memory.InMemoryReactiveCaseMetaModelRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownReactiveCaseMetaModelRepository extends InMemoryReactiveCaseMetaModelRepository {}
