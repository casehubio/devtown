package io.casehub.devtown.review;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewTriageHandlerTest {

  private final ReviewTriageHandler handler = new ReviewTriageHandler();

  private TriageContext ctx(String failureSummary, int attempt, int maxAttempts) {
    return new TriageContext(
        "case-1:code-analysis",
        "worker-execution",
        attempt,
        maxAttempts,
        failureSummary,
        Map.of(),
        Map.of());
  }

  // --- DECLINED failures → always Retry (engine re-routes to next candidate) ---

  @Test
  void declined_outside_capability_triggers_retry() {
    var decision = handler.evaluate(ctx("outside capability: no security expertise", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void declined_not_qualified_triggers_retry() {
    var decision = handler.evaluate(ctx("not qualified for architecture review", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void declined_explicit_triggers_retry() {
    var decision = handler.evaluate(ctx("declined: agent does not support this language", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void declined_on_last_attempt_still_retries() {
    var decision = handler.evaluate(ctx("declined: no capability for this review type", 3, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  // --- Transient faults → Retry with timeout increase ---

  @Test
  void timeout_on_first_attempt_retries() {
    var decision = handler.evaluate(ctx("timeout waiting for agent response", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void rate_limit_on_first_attempt_retries() {
    var decision = handler.evaluate(ctx("429 rate limit exceeded", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void connection_reset_on_first_attempt_retries() {
    var decision = handler.evaluate(ctx("connection reset by peer", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  // --- Unrecoverable failures → always Abandon ---

  @Test
  void unsupported_language_abandons_immediately() {
    var decision = handler.evaluate(ctx("unsupported language: COBOL", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
    assertThat(((TriageDecision.Abandon) decision).reason()).contains("unrecoverable");
  }

  @Test
  void binary_file_abandons_immediately() {
    var decision = handler.evaluate(ctx("binary file cannot be reviewed", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
  }

  @Test
  void exceeds_limit_abandons_immediately() {
    var decision = handler.evaluate(ctx("diff exceeds limit: 50000 lines", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
  }

  // --- Repeated faults → Abandon with escalation ---

  @Test
  void general_fault_on_last_attempt_abandons() {
    var decision = handler.evaluate(ctx("agent crashed during review", 3, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
    assertThat(((TriageDecision.Abandon) decision).reason()).contains("exhausted");
  }

  @Test
  void transient_fault_on_last_attempt_abandons() {
    var decision = handler.evaluate(ctx("timeout waiting for agent response", 3, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
  }

  // --- Unknown failures → retry if budget remains, abandon if exhausted ---

  @Test
  void unknown_failure_on_first_attempt_retries() {
    var decision = handler.evaluate(ctx("unexpected internal error", 1, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
  }

  @Test
  void unknown_failure_on_last_attempt_abandons() {
    var decision = handler.evaluate(ctx("unexpected internal error", 3, 3));
    assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
  }
}
