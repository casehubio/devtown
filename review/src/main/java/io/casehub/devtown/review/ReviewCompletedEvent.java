package io.casehub.devtown.review;

import io.casehub.devtown.domain.memory.ReviewOutcome;
import java.util.UUID;

public record ReviewCompletedEvent(
    UUID caseId,
    String tenantId,
    String capability,
    String reviewerId,
    ReviewOutcome outcome,
    String outcomeDetail,
    PrPayload pr
) {}
