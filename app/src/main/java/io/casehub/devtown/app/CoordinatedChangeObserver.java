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

    private static final Logger LOG = Logger.getLogger(CoordinatedChangeObserver.class);
    private static final Set<String> TERMINAL_SUCCESS = Set.of("COMPLETED");
    private static final Set<String> TERMINAL_FAILURE = Set.of("FAULTED", "CANCELLED", "TERMINATED");

    private final CoordinatedChangeTracker tracker;
    private final CaseHubRuntime caseHubRuntime;

    @Inject
    public CoordinatedChangeObserver(CoordinatedChangeTracker tracker, CaseHubRuntime caseHubRuntime) {
        this.tracker = tracker;
        this.caseHubRuntime = caseHubRuntime;
    }

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) return;
        var entry = tracker.findByReviewCaseId(event.caseId());
        if (entry == null) return;
        if (tracker.isParentTerminal(entry.parentCaseId())) return;

        if (TERMINAL_SUCCESS.contains(event.caseStatus())) {
            if (tracker.markCompleted(entry.parentCaseId(), entry.repo())) {
                caseHubRuntime.signal(entry.parentCaseId(),
                    "completedReviews." + entry.repo(),
                    Map.of("status", "completed", "reviewCaseId", entry.reviewCaseId().toString()));
            }
            if (tracker.tryTransitionToAllCompleted(entry.parentCaseId())) {
                caseHubRuntime.signal(entry.parentCaseId(), "allReviewsCompleted", true);
            }
        } else if (TERMINAL_FAILURE.contains(event.caseStatus())) {
            caseHubRuntime.signal(entry.parentCaseId(), "reviewFaulted",
                Map.of("repo", entry.repo(), "reason", event.caseStatus()));
        }
    }

    void onParentTerminal(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) return;
        if (!TERMINAL_SUCCESS.contains(event.caseStatus()) && !TERMINAL_FAILURE.contains(event.caseStatus())) return;
        if (!"coordinated-change".equals(event.caseDefinitionName())) return;
        var reviewIds = tracker.findReviewCaseIds(event.caseId());
        if (reviewIds.isEmpty()) return;

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
