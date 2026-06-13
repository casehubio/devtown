package io.casehub.devtown.domain;

import java.util.UUID;

public record FlaggedAgent(
    String agentId,
    String capabilityTag,
    UUID attestationId
) {}
