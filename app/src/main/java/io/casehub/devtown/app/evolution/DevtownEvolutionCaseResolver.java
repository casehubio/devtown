package io.casehub.devtown.app.evolution;

import io.casehub.engine.common.spi.CaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class DevtownEvolutionCaseResolver {

  @Inject CaseInstanceRepository caseInstanceRepository;

  public UUID resolve(String tenancyId) {
    return caseInstanceRepository
        .findByNamespaceAndName(
            DevtownEvolutionBootstrap.NAMESPACE,
            DevtownEvolutionBootstrap.NAME,
            tenancyId)
        .stream()
        .findFirst()
        .map(ci -> ci.getUuid())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No evolution case found — DevtownEvolutionBootstrap should have created one"));
  }
}
