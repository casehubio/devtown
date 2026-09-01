package io.casehub.devtown.app;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Observes terminal {@link CaseLifecycleEvent} transitions for merge batch cases
 * and drives queue cleanup and WorkItem obsolescence.
 *
 * <p>Maps case status to batch outcome:
 * <ul>
 *   <li>{@code COMPLETED} → batchSucceeded = true → entries reach {@code MERGED}
 *   <li>{@code CANCELLED} → batchSucceeded = false → entries reach {@code DEQUEUED}
 *   <li>{@code FAULTED} → skipped (no cleanup)
 * </ul>
 *
 * <p>Filters out PR review cases (context has {@code pr.*}) and sub-case lifecycle
 * events (no {@code batch.*} context). Only root batch cases with {@code batch.*}
 * trigger queue cleanup.
 */
@ApplicationScoped
public class MergeBatchCompletionObserver {

    private static final Logger LOG = Logger.getLogger(MergeBatchCompletionObserver.class);

    @Inject MergeQueueService mergeQueueService;

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        try {
            handleEvent(event);
        } catch (Exception e) {
            LOG.warnf(e, "MergeBatchCompletionObserver failed for caseId=%s", event.caseId());
        }
    }

    private void handleEvent(CaseLifecycleEvent event) {
        if (event.caseStatus() == null) {return;}

        Boolean batchSucceeded = switch (event.caseStatus()) {
            case "COMPLETED" -> true;
            case "CANCELLED" -> false;
            default -> {
                LOG.debugf("Ignoring non-terminal case status %s for caseId=%s",
                           event.caseStatus(), event.caseId());
                yield null;
            }
        };
        if (batchSucceeded == null) {return;}

        JsonNode snapshot = event.contextSnapshot();
        if (snapshot == null) {
            LOG.warnf("contextSnapshot is null for caseId=%s", event.caseId());
            return;
        }

        String batchId = snapshot.path("batch").path("id").asText(null);
        String prRepo  = snapshot.path("pr").path("repo").asText(null);
        if (batchId == null || prRepo != null) {
            return;
        }

        Set<Integer> rejectedPrs = extractRejectedPrs(snapshot);

        mergeQueueService.handleBatchCompletion(event.caseId(), batchSucceeded, rejectedPrs);
        LOG.debugf("Batch completion handled: caseId=%s succeeded=%s rejected=%s",
                   event.caseId(), batchSucceeded, rejectedPrs);
    }

    private static Set<Integer> extractRejectedPrs(JsonNode snapshot) {
        JsonNode rejectedNode = snapshot.path("batch").path("rejectedPrs");
        if (!rejectedNode.isArray()) {
            return Set.of();
        }
        Set<Integer> rejected = new HashSet<>();
        for (JsonNode item : rejectedNode) {
            if (item.isNumber()) {
                rejected.add(item.intValue());
            } else if (item.isTextual()) {
                try {
                    rejected.add(Integer.parseInt(item.asText()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return rejected;
    }
}
