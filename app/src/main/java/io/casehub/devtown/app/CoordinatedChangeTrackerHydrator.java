package io.casehub.devtown.app;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CoordinatedChangeTrackerHydrator {

    private static final Logger          LOG      = Logger.getLogger(CoordinatedChangeTrackerHydrator.class);
    private static final Set<CaseStatus> TERMINAL = Set.of(
            CaseStatus.COMPLETED, CaseStatus.FAULTED, CaseStatus.CANCELLED);

    @Inject
    CoordinatedChangeTracker tracker;
    @Inject
    CaseInstanceRepository   caseInstanceRepository;
    @Inject
    CurrentPrincipal         principal;

    void onStartup(@Observes StartupEvent event) {
        hydrate();
    }

    @SuppressWarnings("unchecked")
    void hydrate() {
        String tenancyId = principal.tenancyId();
        var instances = caseInstanceRepository.findByNamespaceAndName(
                "devtown", "coordinated-change", tenancyId);
        int count = 0;

        for (var instance : instances) {
            if (TERMINAL.contains(instance.getState())) {continue;}
            var ctx = instance.getCaseContext();
            if (ctx == null) {continue;}

            var reviewCases = (Map<String, String>) ctx.get("reviewCases");
            if (reviewCases == null) {continue;}

            for (var entry : reviewCases.entrySet()) {
                tracker.register(instance.getUuid(), entry.getKey(),
                                 UUID.fromString(entry.getValue()));
            }

            var completedReviews = (Map<String, Object>) ctx.get("completedReviews");
            if (completedReviews != null) {
                for (String repo : completedReviews.keySet()) {
                    tracker.markCompleted(instance.getUuid(), repo);
                }
            }

            count++;
        }

        if (count > 0) {
            LOG.infof("Hydrated %d active coordinated changes from durable state", count);
        }
    }
}
