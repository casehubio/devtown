package io.casehub.devtown.app.evolution;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.DenyPatternProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class DevtownDenyPatternProvider implements DenyPatternProvider {

  @Override
  public String domainId() {
    return "devtown";
  }

  @Override
  public boolean isDenied(
      UUID caseId, String tenancyId, ImprovementRequest request, ImprovementConfig config) {
    String target = request.target();
    if (target != null && target.contains("security-review")) {
      return true;
    }
    return false;
  }
}
