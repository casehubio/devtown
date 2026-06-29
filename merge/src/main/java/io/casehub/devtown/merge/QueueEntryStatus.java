package io.casehub.devtown.merge;

/**
 * Queue entry lifecycle states.
 *
 * <ul>
 *   <li>QUEUED — admitted to queue, awaiting batch formation</li>
 *   <li>IN_BATCH — currently being tested or merged as part of a batch</li>
 *   <li>MERGED — successfully merged to target branch</li>
 *   <li>REJECTED — batch failed, PR identified as faulty via bisection</li>
 *   <li>DEQUEUED — manually removed from queue before batching</li>
 * </ul>
 */
public enum QueueEntryStatus {
    QUEUED,
    IN_BATCH,
    MERGED,
    REJECTED,
    DEQUEUED
}
