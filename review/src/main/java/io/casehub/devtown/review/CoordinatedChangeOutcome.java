package io.casehub.devtown.review;

import java.util.Map;
import java.util.UUID;

public record CoordinatedChangeOutcome(UUID parentCaseId, Map<String, UUID> reviewCaseIds) {}
