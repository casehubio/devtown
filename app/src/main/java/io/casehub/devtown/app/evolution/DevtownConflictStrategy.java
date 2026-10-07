package io.casehub.devtown.app.evolution;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DevtownConflictStrategy implements ConflictStrategy {

  @Override
  public String domainId() {
    return "devtown";
  }

  @Override
  public ConflictResult check(
      ImprovementRequest request,
      Map<UUID, ImprovementRequest> activeImprovements,
      int trivialThreshold) {
    for (var entry : activeImprovements.entrySet()) {
      if (entry.getValue().category().equals(request.category())
          && "devtown".equals(entry.getValue().domainId())) {
        return new ConflictResult.Conflicting(
            entry.getKey(), "Same category active: " + request.category());
      }
    }
    return new ConflictResult.Clear();
  }
}
