package io.casehub.devtown.app;

import io.casehub.platform.agent.config.AgentManifestResult;
import io.casehub.platform.api.model.ModelChain;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import java.util.Map;

@ApplicationScoped
class TestManifestResultProducer {

    @Produces
    @Alternative
    @Priority(10)
    AgentManifestResult testManifest() {
        var simChain = ModelChain.of("simulation");
        return new AgentManifestResult(
                Map.of(
                        "reasoning-heavy", simChain,
                        "standard-review", simChain,
                        "cheap-fast", simChain),
                "simulation",
                List.of(),
                Map.of());
    }
}
