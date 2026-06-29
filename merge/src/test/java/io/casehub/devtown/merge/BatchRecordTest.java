package io.casehub.devtown.merge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchRecordTest {

    @Test
    void shouldConstructBatchRecord() {
        String batchId = "batch-2026-06-29-xyz789";
        UUID caseId = UUID.randomUUID();
        List<Integer> prNumbers = List.of(123, 456, 789);
        String repository = "casehubio/devtown";
        Instant dispatchedAt = Instant.now();

        BatchRecord record = new BatchRecord(
            batchId,
            caseId,
            prNumbers,
            repository,
            dispatchedAt
        );

        assertThat(record.batchId()).isEqualTo(batchId);
        assertThat(record.caseId()).isEqualTo(caseId);
        assertThat(record.prNumbers()).containsExactly(123, 456, 789);
        assertThat(record.repository()).isEqualTo(repository);
        assertThat(record.dispatchedAt()).isEqualTo(dispatchedAt);
    }

    @Test
    void shouldHandleEmptyPrList() {
        BatchRecord record = new BatchRecord(
            "batch-empty",
            UUID.randomUUID(),
            List.of(),
            "casehubio/test",
            Instant.now()
        );

        assertThat(record.prNumbers()).isEmpty();
    }
}
