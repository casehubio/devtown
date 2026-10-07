package io.casehub.devtown.app.evolution;

import io.casehub.api.view.EvolutionStateSnapshot;
import io.casehub.api.view.EvolutionStateSnapshot.ImprovementStreamView;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DevtownEvolutionEnricher {

  public DevtownEvolutionStateSnapshot enrich(EvolutionStateSnapshot state) {
    var enrichedStreams =
        state.activeStreams().stream().map(this::enrichStream).toList();
    return new DevtownEvolutionStateSnapshot(state, enrichedStreams);
  }

  private DevtownImprovementStreamView enrichStream(ImprovementStreamView stream) {
    String enrichedTarget = resolveTarget(stream.category(), stream.target());
    return new DevtownImprovementStreamView(stream, enrichedTarget);
  }

  private String resolveTarget(String category, String target) {
    if (target == null) {
      return null;
    }
    return switch (category) {
      case "reviewer-calibration" -> resolveReviewerTarget(target);
      case "routing-adjustment" -> "Routing: " + target;
      case "sla-tuning" -> "SLA: " + target;
      case "gate-tightening" -> "Gate: " + target;
      case "queue-optimization" -> "Queue: " + target;
      default -> target;
    };
  }

  private String resolveReviewerTarget(String target) {
    if (target.startsWith("reviewer:")) {
      String reviewerId = target.substring("reviewer:".length());
      return "Reviewer " + reviewerId;
    }
    return target;
  }
}
