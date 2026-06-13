package io.casehub.devtown.domain;

import java.util.UUID;

public record IncidentFeedback(
    String repository,
    int prNumber,
    String incidentId,
    IncidentSeverity severity,
    String description,
    String reviewCapability,
    UUID caseId
) {}
