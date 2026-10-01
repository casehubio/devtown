package io.casehub.devtown.app.spi;

import io.casehub.engine.persistence.memory.InMemoryCaseMetaModelRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownCaseMetaModelRepository extends InMemoryCaseMetaModelRepository {}
