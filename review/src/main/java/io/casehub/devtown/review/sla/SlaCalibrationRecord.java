package io.casehub.devtown.review.sla;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SlaCalibrationRecord(
    UUID id,
    String capability,
    String scopePath,
    Duration median,
    Duration min,
    Duration max,
    int precedentCount,
    UUID caseId,
    Instant computedAt
) {}
