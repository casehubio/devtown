package io.casehub.devtown.app;

import io.casehub.work.runtime.event.SlaBreachEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Observes SLA breach events for merge queue WorkItems and triggers prioritization.
 *
 * <p>Distinguishes merge queue breaches from PR review breaches via {@code callerRef} format:
 * <ul>
 *   <li>Merge queue: {@code "{repository}#{prNumber}"} — e.g., {@code "casehubio/devtown#456"}
 *   <li>PR review: {@code "case:{caseId}:plan-item:{planItemId}"} (plan-item format)
 * </ul>
 *
 * <p>On any merge queue SLA breach (regardless of lane): calls {@link MergeQueueService#prioritize}
 * to mark the PR as prioritized and trigger immediate batch formation bypassing dispatch threshold.
 *
 * <p>Synchronous observer (matches {@link SlaBreachHandler} pattern) — breach handling completes
 * before the breach lifecycle proceeds.
 */
@ApplicationScoped
public class MergeQueueSlaBreachObserver {

    private static final Logger LOG = Logger.getLogger(MergeQueueSlaBreachObserver.class);

    @Inject
    MergeQueueService mergeQueueService;

    void onBreach(@Observes SlaBreachEvent event) {
        try {
            String callerRef = event.context().task().callerRef();
            if (callerRef == null) {
                return;
            }

            // Parse merge queue callerRef: "{repository}#{prNumber}"
            // Example: "casehubio/devtown#456"
            int hashIndex = callerRef.indexOf('#');
            if (hashIndex <= 0 || hashIndex == callerRef.length() - 1) {
                // Not a merge queue callerRef — either:
                // - No '#' → unknown format
                // - '#' at start → empty repository
                // - '#' at end → empty PR number
                // All of these indicate PR review or other WorkItem type
                return;
            }

            String repository = callerRef.substring(0, hashIndex);
            String prNumberStr = callerRef.substring(hashIndex + 1);

            // Additional validation: repository must contain text, prNumber must be numeric
            if (repository.isBlank()) {
                return;
            }

            int prNumber;
            try {
                prNumber = Integer.parseInt(prNumberStr);
            } catch (NumberFormatException e) {
                // Not a numeric PR number → not a merge queue callerRef
                return;
            }

            // Merge queue breach confirmed — prioritize for immediate dispatch
            LOG.infof("Merge queue SLA breach detected for PR #%d from %s — prioritizing",
                prNumber, repository);
            mergeQueueService.prioritize(prNumber, repository);

        } catch (Exception e) {
            LOG.errorf(e, "MergeQueueSlaBreachObserver failed for callerRef=%s — prioritization skipped",
                event.context().task().callerRef());
        }
    }
}
