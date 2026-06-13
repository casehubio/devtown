package io.casehub.devtown.domain;

import java.util.List;
import java.util.UUID;

public record IncidentFeedbackResult(
    UUID caseId,
    int attestationsWritten,
    List<FlaggedAgent> flaggedAgents
) {}
