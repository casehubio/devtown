package io.casehub.devtown.app.spi;

import io.casehub.blocks.routing.agent.CbrOutcomeWeights;
import io.casehub.blocks.routing.agent.DefaultCbrOutcomeWeights;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class DevCbrOutcomeWeights implements CbrOutcomeWeights {

    private final CbrOutcomeWeights delegate = new DefaultCbrOutcomeWeights();

    @Override
    public Map<io.casehub.api.spi.routing.RoutingOutcome, Double> weights() {
        return delegate.weights();
    }
}
