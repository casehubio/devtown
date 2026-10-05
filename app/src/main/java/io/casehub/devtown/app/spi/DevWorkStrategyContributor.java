package io.casehub.devtown.app.spi;

import io.casehub.engine.runtime.routing.EngineStrategyResolver;
import io.casehub.work.api.spi.ClaimSlaPolicy;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class DevWorkStrategyContributor {

    @Inject
    EngineStrategyResolver     resolver;
    @Inject
    @Any
    Instance<ClaimSlaPolicy>   claimPolicies;
    @Inject
    DevWorkerSelectionStrategy workerStrategy;

    void onStart(@Observes StartupEvent ev) {
        claimPolicies.forEach(s -> {
            try {resolver.registerEntry(s, false);} catch (IllegalStateException e) {}
        });
        try {resolver.registerEntry(workerStrategy, false);} catch (IllegalStateException e) {}
    }
}
