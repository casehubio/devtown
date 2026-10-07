package io.casehub.devtown.app.evolution;

import io.casehub.api.view.EvolutionStateSnapshot.ImprovementStreamView;
import jakarta.annotation.Nullable;

public record DevtownImprovementStreamView(
    ImprovementStreamView stream, @Nullable String enrichedTarget) {}
