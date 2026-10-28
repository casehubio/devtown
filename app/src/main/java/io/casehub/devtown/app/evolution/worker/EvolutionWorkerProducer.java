package io.casehub.devtown.app.evolution.worker;

import io.casehub.devtown.domain.evolution.AdjustmentStrategy;
import io.casehub.devtown.domain.evolution.PreferenceReader;
import io.casehub.devtown.domain.evolution.PreferenceWriter;
import io.casehub.devtown.domain.evolution.ProportionalAdjustmentStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EvolutionWorkerProducer {

  private static final Logger LOG = Logger.getLogger(EvolutionWorkerProducer.class);

  @Produces
  @ApplicationScoped
  AdjustmentStrategy adjustmentStrategy() {
    return new ProportionalAdjustmentStrategy();
  }

  @Produces
  @ApplicationScoped
  PreferenceWriter preferenceWriter() {
    return (scope, namespace, key, value) ->
        LOG.infof("Evolution preference write: scope=%s ns=%s key=%s value=%s",
            scope, namespace, key, value);
  }

    @Produces
    @ApplicationScoped
    PreferenceReader preferenceReader() {
        return (scope, namespace, key) -> java.util.OptionalDouble.empty();
    }

}
