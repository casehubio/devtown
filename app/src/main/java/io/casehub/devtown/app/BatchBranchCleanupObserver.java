package io.casehub.devtown.app;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.devtown.app.mcp.CaseTrackingStatus;
import io.casehub.devtown.domain.BatchBranchClient;
import io.casehub.devtown.domain.BranchDeleteOutcome;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BatchBranchCleanupObserver {

    private static final Logger LOG = Logger.getLogger(BatchBranchCleanupObserver.class);

    @Inject
    BatchBranchClient batchBranchClient;

    void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (event.caseStatus() == null) return;
        if (!CaseTrackingStatus.fromCaseStatus(event.caseStatus()).isTerminal()) return;
        if (!"devtown".equals(event.namespace())) return;
        if (!"merge-batch".equals(event.caseDefinitionName())) return;

        JsonNode context = event.contextSnapshot();
        if (context == null) return;

        JsonNode batch = context.path("batch");
        String repository = batch.path("repository").asText(null);
        if (repository == null || !repository.contains("/")) return;
        String[] parts = repository.split("/");

        String batchId = batch.path("id").asText(null);
        if (batchId == null) return;
        String branchName = "merge-queue/batch-" + batchId;

        var result = batchBranchClient.deleteBatchBranch(parts[0], parts[1], branchName);
        switch (result) {
            case BranchDeleteOutcome.Deleted d ->
                LOG.infof("Cleaned up batch branch %s for case %s", d.branchName(), event.caseId());
            case BranchDeleteOutcome.NotFound nf ->
                LOG.debugf("Batch branch %s already gone for case %s", nf.branchName(), event.caseId());
            case BranchDeleteOutcome.Failure f ->
                LOG.warnf("Failed to clean up batch branch for case %s: %s", event.caseId(), f.reason());
        }
    }
}
