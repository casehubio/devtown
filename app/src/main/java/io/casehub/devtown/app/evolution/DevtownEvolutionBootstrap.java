package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.CaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class DevtownEvolutionBootstrap {

  static final String NAMESPACE = "devtown";
  static final String NAME = "evolution";
  static final String TENANCY_ID = "default";

  @Inject CaseInstanceRepository caseInstanceRepository;
  @Inject DevtownEvolutionCaseHub caseHub;

  void onStartup(@Observes StartupEvent event) {
    var existing = caseInstanceRepository.findByNamespaceAndName(NAMESPACE, NAME, TENANCY_ID);
    if (existing.isEmpty()) {
      caseHub.startCase();
    }
  }
}
