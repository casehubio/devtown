package io.casehub.devtown.app.ledger;

import io.casehub.ledger.api.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "contributor_outcome_ledger_entry", indexes = {
        @Index(name = "idx_contributor_outcome_repo_pr", columnList = "repository, pr_number"),
        @Index(name = "idx_contributor_outcome_case_id", columnList = "case_id")
})
@DiscriminatorValue("CONTRIBUTOR_OUTCOME")
@NamedQuery(
        name = "ContributorOutcomeLedgerEntry.countByRepoAndPrAndOutcome",
        query = "SELECT COUNT(c) FROM ContributorOutcomeLedgerEntry c " +
                "WHERE c.repository = :repo AND c.prNumber = :prNumber AND c.outcome = :outcome"
)
public class ContributorOutcomeLedgerEntry extends LedgerEntry {

    @Column(name = "pr_number", nullable = false)
    public int prNumber;

    @Column(name = "repository", nullable = false, length = 255)
    public String repository;

    @Column(name = "outcome", nullable = false, length = 30)
    public String outcome;

    @Column(name = "review_rounds")
    public Integer reviewRounds;

    @Column(name = "case_id")
    public UUID caseId;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                String.valueOf(prNumber),
                LedgerContentUtils.escapePipe(repository),
                LedgerContentUtils.escapePipe(outcome),
                reviewRounds != null ? String.valueOf(reviewRounds) : "",
                caseId != null ? caseId.toString() : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
