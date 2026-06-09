package io.casehub.devtown.app.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "merge_decision_ledger_entry", indexes = {
    @Index(name = "idx_merge_decision_entry_case_id", columnList = "case_id"),
    @Index(name = "idx_merge_decision_entry_tenancy_id", columnList = "tenancy_id")
})
@DiscriminatorValue("MERGE_DECISION")
public class MergeDecisionLedgerEntry extends LedgerEntry {

    @Column(name = "pr_number", nullable = false)
    public int prNumber;

    @Column(name = "repository", nullable = false, length = 255)
    public String repository;

    @Column(name = "commit_sha", length = 40)
    public String commitSha;

    @Column(name = "decision", nullable = false, length = 20)
    public String decision;

    @Column(name = "case_id", nullable = false)
    public UUID caseId;

    @Column(name = "tenancy_id", nullable = false, length = 64)
    public String tenancyId;
}
