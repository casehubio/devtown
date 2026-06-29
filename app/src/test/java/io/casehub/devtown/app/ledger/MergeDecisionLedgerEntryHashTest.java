package io.casehub.devtown.app.ledger;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * TDD for MergeDecisionLedgerEntry batch metadata hash semantics.
 *
 * Requirements from R1-05:
 * - domainContentBytes() must be deterministic
 * - Scalar batch fields (batchId, batchSize, bisectionOccurred, bisectionStrategy) ARE in the hash
 * - batchContextJson is EXCLUDED from the hash (JSON serialization is non-deterministic)
 * - Null fields hash to empty string
 */
class MergeDecisionLedgerEntryHashTest {

    @Test
    void sameEntityProducesIdenticalHashAcrossTwoCalls() {
        var entry = new MergeDecisionLedgerEntry();
        entry.prNumber = 123;
        entry.repository = "casehubio/devtown";
        entry.commitSha = "abc123";
        entry.decision = "APPROVED";
        entry.caseId = UUID.randomUUID();
        entry.batchId = "batch-001";
        entry.batchSize = 5;
        entry.bisectionOccurred = false;
        entry.bisectionStrategy = "trust-weighted";
        entry.batchContextJson = "{\"prList\": [456, 457]}";

        byte[] hash1 = entry.domainContentBytes();
        byte[] hash2 = entry.domainContentBytes();

        assertArrayEquals(hash1, hash2, "Same entity must produce identical hash across two calls (determinism)");
    }

    @Test
    void nullBatchFieldsProduceConsistentHash() {
        var entry1 = new MergeDecisionLedgerEntry();
        entry1.prNumber = 123;
        entry1.repository = "casehubio/devtown";
        entry1.commitSha = "abc123";
        entry1.decision = "APPROVED";
        entry1.caseId = UUID.randomUUID();
        // All batch fields null

        var entry2 = new MergeDecisionLedgerEntry();
        entry2.prNumber = 123;
        entry2.repository = "casehubio/devtown";
        entry2.commitSha = "abc123";
        entry2.decision = "APPROVED";
        entry2.caseId = entry1.caseId; // Same UUID
        // All batch fields null

        byte[] hash1 = entry1.domainContentBytes();
        byte[] hash2 = entry2.domainContentBytes();

        assertArrayEquals(hash1, hash2, "Entries with null batch fields must produce identical hash");
    }

    @Test
    void batchContextJsonChangesDoNotChangeHash() {
        var entry1 = new MergeDecisionLedgerEntry();
        entry1.prNumber = 123;
        entry1.repository = "casehubio/devtown";
        entry1.commitSha = "abc123";
        entry1.decision = "APPROVED";
        entry1.caseId = UUID.randomUUID();
        entry1.batchId = "batch-001";
        entry1.batchSize = 5;
        entry1.bisectionOccurred = false;
        entry1.bisectionStrategy = "trust-weighted";
        entry1.batchContextJson = "{\"prList\": [456, 457]}";

        var entry2 = new MergeDecisionLedgerEntry();
        entry2.prNumber = 123;
        entry2.repository = "casehubio/devtown";
        entry2.commitSha = "abc123";
        entry2.decision = "APPROVED";
        entry2.caseId = entry1.caseId;
        entry2.batchId = "batch-001";
        entry2.batchSize = 5;
        entry2.bisectionOccurred = false;
        entry2.bisectionStrategy = "trust-weighted";
        entry2.batchContextJson = "{\"prList\": [789, 790]}"; // Different JSON content

        byte[] hash1 = entry1.domainContentBytes();
        byte[] hash2 = entry2.domainContentBytes();

        assertArrayEquals(hash1, hash2, "batchContextJson changes must NOT change the hash");
    }

    @Test
    void batchIdChangesDoChangeHash() {
        var entry1 = new MergeDecisionLedgerEntry();
        entry1.prNumber = 123;
        entry1.repository = "casehubio/devtown";
        entry1.commitSha = "abc123";
        entry1.decision = "APPROVED";
        entry1.caseId = UUID.randomUUID();
        entry1.batchId = "batch-001";
        entry1.batchSize = 5;
        entry1.bisectionOccurred = false;
        entry1.bisectionStrategy = "trust-weighted";

        var entry2 = new MergeDecisionLedgerEntry();
        entry2.prNumber = 123;
        entry2.repository = "casehubio/devtown";
        entry2.commitSha = "abc123";
        entry2.decision = "APPROVED";
        entry2.caseId = entry1.caseId;
        entry2.batchId = "batch-002"; // Different batch ID
        entry2.batchSize = 5;
        entry2.bisectionOccurred = false;
        entry2.bisectionStrategy = "trust-weighted";

        byte[] hash1 = entry1.domainContentBytes();
        byte[] hash2 = entry2.domainContentBytes();

        assertNotEquals(hash1, hash2, "batchId changes must change the hash");
    }
}
