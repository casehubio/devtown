package io.casehub.devtown.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for active batch metadata.
 *
 * <p>Links a batch case ID to the batch's member PR numbers.
 */
@Entity
@Table(name = "merge_queue_batch", indexes = {
    @Index(name = "idx_merge_queue_batch_case_id", columnList = "case_id")
})
public class ActiveBatchEntity {

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
}
