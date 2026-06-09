package io.casehub.devtown.review.compliance;

import java.time.Instant;
import java.util.UUID;

public record LedgerEventRecord(
    UUID entryId,
    String eventType,
    String actorId,
    String actorRole,
    Instant occurredAt,
    UUID causedByEntryId,
    String digest,
    InclusionProofRecord inclusionProof
) {}
