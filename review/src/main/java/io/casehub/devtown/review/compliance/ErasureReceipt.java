package io.casehub.devtown.review.compliance;

import java.time.Instant;
import java.util.UUID;

public record ErasureReceipt(
    String actorId,
    Instant erasedAt,
    long ledgerEntriesAffected,
    int memoryRecordsErased,
    UUID receiptEntryId,
    String reason
) {}
