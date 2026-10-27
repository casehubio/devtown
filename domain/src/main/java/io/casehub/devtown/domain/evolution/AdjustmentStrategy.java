package io.casehub.devtown.domain.evolution;

import java.util.UUID;

public interface AdjustmentStrategy {

  AdjustmentResult compute(
      String parameter,
      double currentValue,
      double healthScore,
      double targetHealth,
      AdjustmentBounds bounds,
      String category,
      UUID caseId);
}
