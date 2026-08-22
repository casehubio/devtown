package io.casehub.devtown.app.mcp;

import io.casehub.neocortex.memory.Memory;
import java.time.Instant;

public record ReasoningTrace(
    String workerName,
    String capability,
    String outcome,
    String reasoning,
    Instant timestamp,
    boolean truncated) {

  static ReasoningTrace from(Memory m) {
    return new ReasoningTrace(
        m.attributes().getOrDefault("workerName", "unknown"),
        m.attributes().getOrDefault("capability", "unknown"),
        m.attributes().getOrDefault("outcome", "unknown"),
        m.text(),
        m.createdAt(),
        "true".equals(m.attributes().get("truncated")));
  }
}
