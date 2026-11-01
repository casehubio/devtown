package io.casehub.devtown.review;

import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import io.casehub.platform.api.governance.TriageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
@Priority(1)
public class ReviewTriageHandler implements TriageHandler {

  @Override
  public TriageDecision evaluate(TriageContext context) {
    String summary = context.failureSummary().toLowerCase();

    if (isDeclined(summary)) {
      return new TriageDecision.Retry(Map.of());
    }

    if (isUnrecoverable(summary)) {
      return new TriageDecision.Abandon(
          "unrecoverable review failure: " + context.failureSummary());
    }

    if (context.attemptNumber() >= context.maxAttempts()) {
      return new TriageDecision.Abandon(
          "review triage exhausted after " + context.attemptNumber()
              + " attempts: " + context.failureSummary());
    }

    return new TriageDecision.Retry(Map.of());
  }

  private static boolean isDeclined(String summary) {
    return summary.contains("declined")
        || summary.contains("outside capability")
        || summary.contains("not qualified")
        || summary.contains("no capability")
        || summary.contains("no expertise");
  }

  private static boolean isUnrecoverable(String summary) {
    return summary.contains("unsupported language")
        || summary.contains("binary file")
        || summary.contains("exceeds limit")
        || summary.contains("too large");
  }
}
