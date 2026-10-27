package io.casehub.devtown.domain.evolution;

import java.util.Map;

public record AdjustmentResult(
    String parameter,
    double oldValue,
    double newValue,
    String method,
    int precedentCount,
    String rationale) {

  public Map<String, Object> toMap() {
    return Map.of(
        "parameter", parameter,
        "oldValue", oldValue,
        "newValue", newValue,
        "method", method,
        "precedentCount", precedentCount,
        "rationale", rationale);
  }
}
