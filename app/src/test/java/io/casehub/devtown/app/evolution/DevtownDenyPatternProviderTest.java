package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevtownDenyPatternProviderTest {

  private final DevtownDenyPatternProvider provider = new DevtownDenyPatternProvider();
  private final UUID caseId = UUID.randomUUID();
  private final String tenancyId = "test-tenant";
  private final ImprovementConfig config =
      new ImprovementConfig(null, null, null, null, null);

  @Test
  void domainId_returns_devtown() {
    assertThat(provider.domainId()).isEqualTo("devtown");
  }

  @Test
  void denies_security_review_target() {
    var request = new ImprovementRequest(
        "recalibrate", "reviewer-calibration", "security-review",
        3, Map.of(), "devtown");
    assertThat(provider.isDenied(caseId, tenancyId, request, config)).isTrue();
  }

  @Test
  void denies_target_containing_security_review() {
    var request = new ImprovementRequest(
        "adjust-routing", "routing-adjustment", "capability:security-review:agent-1",
        3, Map.of(), "devtown");
    assertThat(provider.isDenied(caseId, tenancyId, request, config)).isTrue();
  }

  @Test
  void allows_non_security_target() {
    var request = new ImprovementRequest(
        "recalibrate", "reviewer-calibration", "reviewer:agent-perf",
        3, Map.of(), "devtown");
    assertThat(provider.isDenied(caseId, tenancyId, request, config)).isFalse();
  }

  @Test
  void allows_null_target() {
    var request = new ImprovementRequest(
        "queue-opt", "queue-optimization", null, 2, Map.of(), "devtown");
    assertThat(provider.isDenied(caseId, tenancyId, request, config)).isFalse();
  }
}
