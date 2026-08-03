package io.casehub.devtown.app.ledger;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContributorOutcomeLedgerEntryHashTest {

    @Test
    void sameEntityProducesIdenticalHashAcrossTwoCalls() {
        var entry = new ContributorOutcomeLedgerEntry();
        entry.prNumber = 42;
        entry.repository = "casehubio/devtown";
        entry.outcome = "MERGED";
        entry.reviewRounds = 0;
        entry.caseId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        byte[] hash1 = entry.domainContentBytes();
        byte[] hash2 = entry.domainContentBytes();

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void nullFieldsProduceConsistentHash() {
        var entry1 = new ContributorOutcomeLedgerEntry();
        entry1.prNumber = 42;
        entry1.repository = "casehubio/devtown";
        entry1.outcome = "TRIAGE_REJECTED";
        entry1.reviewRounds = null;
        entry1.caseId = null;

        var entry2 = new ContributorOutcomeLedgerEntry();
        entry2.prNumber = 42;
        entry2.repository = "casehubio/devtown";
        entry2.outcome = "TRIAGE_REJECTED";
        entry2.reviewRounds = null;
        entry2.caseId = null;

        assertThat(entry1.domainContentBytes()).isEqualTo(entry2.domainContentBytes());
    }

    @Test
    void outcomeChangeChangesHash() {
        var entry1 = new ContributorOutcomeLedgerEntry();
        entry1.prNumber = 42;
        entry1.repository = "casehubio/devtown";
        entry1.outcome = "MERGED";
        entry1.reviewRounds = 0;
        entry1.caseId = UUID.randomUUID();

        var entry2 = new ContributorOutcomeLedgerEntry();
        entry2.prNumber = 42;
        entry2.repository = "casehubio/devtown";
        entry2.outcome = "REJECTED";
        entry2.reviewRounds = 0;
        entry2.caseId = entry1.caseId;

        assertThat(entry1.domainContentBytes()).isNotEqualTo(entry2.domainContentBytes());
    }

    @Test
    void reviewRoundsChangeChangesHash() {
        var entry1 = new ContributorOutcomeLedgerEntry();
        entry1.prNumber = 42;
        entry1.repository = "casehubio/devtown";
        entry1.outcome = "MERGED_WITH_REWORK";
        entry1.reviewRounds = 1;
        entry1.caseId = UUID.randomUUID();

        var entry2 = new ContributorOutcomeLedgerEntry();
        entry2.prNumber = 42;
        entry2.repository = "casehubio/devtown";
        entry2.outcome = "MERGED_WITH_REWORK";
        entry2.reviewRounds = 3;
        entry2.caseId = entry1.caseId;

        assertThat(entry1.domainContentBytes()).isNotEqualTo(entry2.domainContentBytes());
    }
}
