package io.casehub.devtown.app.evolution;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevtownEvolutionCaseHub extends YamlCaseHub {

  public DevtownEvolutionCaseHub() {
    super("casehub/devtown/evolution.yaml");
  }
}
