package io.casehub.devtown.merge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Batch metadata — associates a batch case with its member PRs.
 *
 * @param batchId the batch identifier (e.g., "batch-2026-06-29-abc123")
 * @param caseId the CaseHub case ID for the batch lifecycle
 * @param prNumbers PR numbers included in this batch (all from the same repository)
 * @param repository the target repository for all PRs in this batch
 * @param dispatchedAt when the batch case was created
 * @param completedAt when the batch completed (null if still active)
 * @param succeeded true if all PRs merged, false if batch failed, null if active
 */
public record BatchRecord(
    String batchId,
    UUID caseId,
    List<Integer> prNumbers,
    String repository,
    Instant dispatchedAt,
    Instant completedAt,
    Boolean succeeded
) {
    public boolean isActive() { return completedAt == null; }
}
