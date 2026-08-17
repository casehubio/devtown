package io.casehub.devtown.template;

import io.casehub.api.engine.YamlCaseHub;

/**
 * Reusable PR review case definition template.
 *
 * <p>Provides the structural pattern: content-driven review activation, failure cascades with scope
 * reduction, human escalation tiers, CBR configuration, and goal-based completion.
 *
 * <p>Abstract because the merge-executor worker function requires a domain-specific {@code
 * MergeClient} implementation. Consumers extend this class and provide their merge logic via {@link
 * #augment}.
 */
public abstract class PrReviewTemplateCaseHub extends YamlCaseHub {

  protected PrReviewTemplateCaseHub() {
    super("templates/pr-review.yaml");
  }

  protected PrReviewTemplateCaseHub(String overlayPath) {
    super("templates/pr-review.yaml", overlayPath);
  }
}
