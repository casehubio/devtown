package io.casehub.devtown.app.spi;

import io.casehub.engine.flow.PlaybookWorkerFunctionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PlaybookProviderProducer {

  @Produces
  @ApplicationScoped
  PlaybookWorkerFunctionProvider playbookWorkerFunctionProvider() {
    return new PlaybookWorkerFunctionProvider();
  }
}
