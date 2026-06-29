package io.casehub.devtown.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for queued PR state.
 *
 * <p>Uses composite primary key (prNumber, repository) — PR numbers are scoped to repositories.
 */
@Entity
@Table(name = "merge_queue_entry", indexes = {
    @Index(name = "idx_merge_queue_entry_status", columnList = "status"),
    @Index(name = "idx_merge_queue_entry_batch_id", columnList = "batch_id")
})
@IdClass(QueuedPrEntity.QueuedPrId.class)
public class QueuedPrEntity {

    @Id
    @Column(name = "pr_number", nullable = false)
    public int prNumber;

    @Id
    @Column(name = "repository", nullable = false, length = 255)
    public String repository;

    @Column(name = "head_sha", nullable = false, length = 40)
    public String headSha;

    @Column(name = "author", nullable = false, length = 255)
    public String author;

    @Column(name = "trust_score", nullable = false)
    public double trustScore;

    @Column(name = "lane", nullable = false, length = 20)
    public String lane;

    @Column(name = "enqueued_at", nullable = false)
    public Instant enqueuedAt;

    @Column(name = "depends_on", columnDefinition = "TEXT")
    public String dependsOn;

    @Column(name = "work_item_id")
    public UUID workItemId;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "prioritized", nullable = false)
    public boolean prioritized;

    @Column(name = "batch_id", length = 100)
    public String batchId;

    /**
     * Composite primary key class for {@link QueuedPrEntity}.
     */
    public static class QueuedPrId implements Serializable {
        public int prNumber;
        public String repository;

        public QueuedPrId() {
        }

        public QueuedPrId(int prNumber, String repository) {
            this.prNumber = prNumber;
            this.repository = repository;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueuedPrId that)) return false;
            return prNumber == that.prNumber && Objects.equals(repository, that.repository);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prNumber, repository);
        }
    }
}
