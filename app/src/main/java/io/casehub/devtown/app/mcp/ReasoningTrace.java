package io.casehub.devtown.app.mcp;

import io.casehub.neocortex.memory.Memory;
import java.time.Instant;

public record ReasoningTrace(
    String workerName,
    String capability,
    String outcome,
    String reasoning,
    Instant timestamp,
    boolean truncated,
    String module,
    String repo) {

  static ReasoningTrace from(Memory m) {
    return new ReasoningTrace(
        m.attributes().getOrDefault("workerName", "unknown"),
        m.attributes().getOrDefault("capability", "unknown"),
        m.attributes().getOrDefault("outcome", "unknown"),
        m.text(),
        m.createdAt(),
        "true".equals(m.attributes().get("truncated")),
        m.attributes().get("module"),
        m.attributes().get("repo"));
  }

  ReasoningTrace withModuleContext(String module, String repo) {
    return new ReasoningTrace(workerName, capability, outcome, reasoning,
        timestamp, truncated, module, repo);
  }
}
