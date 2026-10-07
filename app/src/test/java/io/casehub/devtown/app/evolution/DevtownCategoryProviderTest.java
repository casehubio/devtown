package io.casehub.devtown.app.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevtownCategoryProviderTest {

  private final DevtownCategoryProvider provider = new DevtownCategoryProvider();

  @Test
  void domainId_returns_devtown() {
    assertThat(provider.domainId()).isEqualTo("devtown");
  }

  @Test
  void provides_five_categories() {
    List<CategoryDescriptor> cats = provider.categories();
    assertThat(cats).hasSize(5);
    assertThat(cats)
        .extracting(CategoryDescriptor::id)
        .containsExactlyInAnyOrder(
            "reviewer-calibration",
            "routing-adjustment",
            "sla-tuning",
            "gate-tightening",
            "queue-optimization");
    assertThat(cats).allMatch(c -> "devtown".equals(c.domainId()));
  }

  @Test
  void provides_five_stages_in_order() {
    List<StageDescriptor> stages = provider.stages();
    assertThat(stages).hasSize(5);
    assertThat(stages)
        .extracting(StageDescriptor::id)
        .containsExactly("analyze", "propose", "review", "apply", "observe");
    assertThat(stages).extracting(StageDescriptor::ordinal).containsExactly(0, 1, 2, 3, 4);
  }

  @Test
  void propose_and_review_are_gate_checkpoints() {
    List<StageDescriptor> stages = provider.stages();
    assertThat(
            stages.stream()
                .filter(StageDescriptor::gateCheckpoint)
                .map(StageDescriptor::id)
                .toList())
        .containsExactlyInAnyOrder("propose", "review");
  }
}
