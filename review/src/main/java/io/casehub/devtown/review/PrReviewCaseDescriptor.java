package io.casehub.devtown.review;

import io.casehub.devtown.domain.AgentQualification;
import io.casehub.devtown.domain.FailurePolicy;
import io.casehub.devtown.domain.ReviewDomain;
import java.time.Duration;
import java.util.Map;

/**
 * Descriptor for PR review failure policies — one per capability.
 *
 * <p>Pre-staged for engine#501: when the engine supports {@code outcomePolicy},
 * {@code contextWrite}, and {@code inputSchemaOverride}, the fluent DSL companion
 * will read these policies to generate Tier 3 (scope reduction) and Tier 4
 * (human escalation) bindings programmatically.
 */
public final class PrReviewCaseDescriptor {

    public static final Map<String, FailurePolicy> FAILURE_POLICIES = Map.of(
        ReviewDomain.SECURITY_REVIEW,       new FailurePolicy(true,  "{ flaggedFiles: .codeAnalysis.flaggedFiles }", Duration.ofHours(4)),
        ReviewDomain.ARCHITECTURE_REVIEW,   new FailurePolicy(true,  "{ crossingPoints: .codeAnalysis.crossingPoints }", Duration.ofHours(4)),
        ReviewDomain.STYLE_REVIEW,          new FailurePolicy(false, null, Duration.ofHours(2)),
        ReviewDomain.TEST_COVERAGE,         new FailurePolicy(false, null, Duration.ofHours(2)),
        ReviewDomain.PERFORMANCE_ANALYSIS,  new FailurePolicy(false, null, Duration.ofHours(2)),
        ReviewDomain.CODE_ANALYSIS,         new FailurePolicy(false, null, Duration.ofHours(1)),
        AgentQualification.CI_RUNNER,       new FailurePolicy(false, null, Duration.ofHours(1))
    );

    private PrReviewCaseDescriptor() {}
}
