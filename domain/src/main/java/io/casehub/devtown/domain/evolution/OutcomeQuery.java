package io.casehub.devtown.domain.evolution;

import java.util.List;

@FunctionalInterface
public interface OutcomeQuery {
  List<EvolutionOutcomeRecord> findByCategory(String category, int limit);
}
