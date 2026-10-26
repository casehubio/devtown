package io.casehub.devtown.app;

import io.casehub.platform.agent.config.ManifestResult;
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
    ManifestResult testManifest() {
        var simChain = ModelChain.of("simulation");
        return new ManifestResult(
                Map.of(
                        "reasoning-heavy", simChain,
                        "standard-review", simChain,
                        "cheap-fast", simChain),
                "simulation",
                List.of(),
                Map.of());
    }
}
