package io.casehub.devtown.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for batch metadata.
 *
 * <p>Links a batch case ID to the batch's member PR numbers and tracks lifecycle.
 */
@Entity
@Table(name = "merge_queue_batch", indexes = {
    @Index(name = "idx_merge_queue_batch_case_id", columnList = "case_id"),
    @Index(name = "idx_merge_queue_batch_repo_completed", columnList = "repository, completed_at")
})
public class BatchEntity {

    @Id
    @Column(name = "batch_id", nullable = false, length = 100)
    public String batchId;

    @Column(name = "case_id", nullable = false)
    public UUID caseId;

    @Column(name = "pr_numbers", nullable = false, columnDefinition = "TEXT")
    public String prNumbers;

    @Column(name = "repository", nullable = false, length = 255)
    public String repository;

    @Column(name = "dispatched_at", nullable = false)
    public Instant dispatchedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "succeeded")
    public Boolean succeeded;
}
