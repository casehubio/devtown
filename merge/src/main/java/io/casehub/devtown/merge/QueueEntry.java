package io.casehub.devtown.merge;

import io.casehub.devtown.queue.QueuedPr;

import java.util.UUID;

/**
 * Persistent queue entry — combines the domain PR representation with queue metadata.
 *
 * @param pr the queued PR
 * @param workItemId the SLA WorkItem tracking queue wait time
 * @param status lifecycle state (QUEUED, IN_BATCH, MERGED, REJECTED, DEQUEUED)
 * @param prioritized true if marked for immediate dispatch (e.g., after SLA breach)
 * @param batchId null until the PR is batched; set when status → IN_BATCH
 */
public record QueueEntry(
    QueuedPr pr,
    UUID workItemId,
    QueueEntryStatus status,
    boolean prioritized,
    String batchId
) {}
