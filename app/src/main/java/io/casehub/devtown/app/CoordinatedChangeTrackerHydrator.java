package io.casehub.devtown.app;

import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class CoordinatedChangeTrackerHydrator {

    private static final Logger LOG = Logger.getLogger(CoordinatedChangeTrackerHydrator.class);

    @Inject CoordinatedChangeTracker tracker;
    @Inject CaseHubRuntime caseHubRuntime;

    void onStartup(@Observes StartupEvent event) {
        LOG.info("CoordinatedChangeTrackerHydrator startup — hydration deferred until CaseInstanceRepository query API is available");
    }

    @SuppressWarnings("unchecked")
    static void hydrateFromContext(CoordinatedChangeTracker tracker, UUID parentCaseId, Map<String, Object> parentContext) {
        var reviewCases = (Map<String, String>) parentContext.get("reviewCases");
        if (reviewCases == null) return;
        for (var entry : reviewCases.entrySet()) {
            tracker.register(parentCaseId, entry.getKey(), UUID.fromString(entry.getValue()));
        }
    }
}
