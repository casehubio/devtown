package io.casehub.devtown.app.spi;

import io.casehub.engine.persistence.memory.InMemorySubCaseGroupRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownSubCaseGroupRepository extends InMemorySubCaseGroupRepository {}
