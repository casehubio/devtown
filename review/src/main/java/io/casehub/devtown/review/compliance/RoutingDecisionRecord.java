package io.casehub.devtown.review.compliance;

import java.util.UUID;

public record RoutingDecisionRecord(
    String capabilityTag,
    String workerId,
    Double trustScoreAtRouting,
    Double thresholdApplied,
    UUID ledgerEntryId
) {}
