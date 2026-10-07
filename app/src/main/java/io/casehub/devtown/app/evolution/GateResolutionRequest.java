package io.casehub.devtown.app.evolution;

import io.casehub.api.model.stigmergy.ConductorInboxEntry;
import jakarta.annotation.Nullable;

public record GateResolutionRequest(
    ConductorInboxEntry.Status outcome,
    @Nullable String reason,
    @Nullable String feedback) {}
