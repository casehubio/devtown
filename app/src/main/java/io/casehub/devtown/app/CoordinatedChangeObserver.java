package io.casehub.devtown.app;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CoordinatedChangeObserver {

    private static final Logger      LOG      = Logger.getLogger(CoordinatedChangeObserver.class);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAULTED", "CANCELLED");

    private final CoordinatedChangeTracker tracker;
    private final CaseHubRuntime           caseHubRuntime;

    @Inject
    public CoordinatedChangeObserver(CoordinatedChangeTracker tracker, CaseHubRuntime caseHubRuntime) {
        this.tracker        = tracker;
        this.caseHubRuntime = caseHubRuntime;
    }

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) {return;}
        var entry = tracker.findByReviewCaseId(event.caseId());
        if (entry == null) {return;}
        if (tracker.isParentTerminal(entry.parentCaseId())) {return;}

        String status = event.caseStatus();
        var provenance = Map.<String, Object>of(
                "causedByCaseId", entry.reviewCaseId().toString(),
                "causedByEvent", event.eventType() != null ? event.eventType() : status);

        if ("COMPLETED".equals(status) && "failure".equals(event.satisfiedGoalKind())) {
            caseHubRuntime.signal(entry.parentCaseId(), "reviewFaulted",
                                  Map.of("repo", entry.repo(), "reason", event.satisfiedGoalName()),
                                  provenance);
        } else if ("COMPLETED".equals(status)) {
            if (tracker.markCompleted(entry.parentCaseId(), entry.repo())) {
                caseHubRuntime.signal(entry.parentCaseId(),
                                      "completedReviews." + entry.repo(),
                                      Map.of("status", "completed", "reviewCaseId", entry.reviewCaseId().toString()),
                                      provenance);
            }
            if (tracker.tryTransitionToAllCompleted(entry.parentCaseId())) {
                caseHubRuntime.signal(entry.parentCaseId(), "allReviewsCompleted", true,
                                      provenance);
            }
        } else if ("FAULTED".equals(status) || "CANCELLED".equals(status)) {
            caseHubRuntime.signal(entry.parentCaseId(), "reviewFaulted",
                                  Map.of("repo", entry.repo(), "reason", status),
                                  provenance);
        }
    }

    void onParentTerminal(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) {return;}
        if (!TERMINAL.contains(event.caseStatus())) {return;}
        if (!"coordinated-change".equals(event.caseDefinitionName())) {return;}
        var reviewIds = tracker.findReviewCaseIds(event.caseId());
        if (reviewIds.isEmpty()) {return;}

        tracker.markParentTerminal(event.caseId());
        for (var reviewId : reviewIds) {
            try {
                caseHubRuntime.cancelCase(reviewId);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to cancel review case %s during parent terminal propagation", reviewId);
            }
        }
    }
}
