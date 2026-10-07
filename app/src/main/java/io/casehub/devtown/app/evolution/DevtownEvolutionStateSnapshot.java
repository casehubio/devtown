package io.casehub.devtown.app.evolution;

import io.casehub.api.view.EvolutionStateSnapshot;
import java.util.List;

public record DevtownEvolutionStateSnapshot(
    EvolutionStateSnapshot engineState,
    List<DevtownImprovementStreamView> enrichedStreams) {}
