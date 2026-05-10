package io.casehub.devtown.domain;

import io.casehub.devtown.domain.spi.CapabilityRegistry;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalDouble;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevtownCapabilityRegistryTest {

    private final DevtownCapabilityRegistry registry = new DevtownCapabilityRegistry();

    // === capabilities() ===

    @Test
    void capabilitiesContainsAllTenExpectedValues() {
        assertThat(registry.capabilities()).containsExactlyInAnyOrder(
            "code-analysis", "security-review", "architecture-review",
            "style-review", "test-coverage", "performance-analysis",
            "ci-runner", "merge-executor",
            "human-decision:pr-approval", "human-oversight:routing-review"
        );
    }

    @Test
    void capabilitiesReturnsImmutableSet() {
        assertThatThrownBy(() -> registry.capabilities().add("intruder"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // === policy() — trust-gated capabilities ===

    @Test
    void securityReviewThresholdIs070() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.threshold().getAsDouble()).isEqualTo(0.70);
    }

    @Test
    void securityReviewMinimumObservationsIs10() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.minimumObservations().getAsInt()).isEqualTo(10);
    }

    @Test
    void securityReviewBorderlineMarginIs005() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.borderlineMargin().getAsDouble()).isEqualTo(0.05);
    }

    @Test
    void securityReviewFallbackIsHumanOversightRoutingReview() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.fallbackType()).contains(HumanOversight.ROUTING_REVIEW);
    }

    @Test
    void architectureReviewThresholdIs065() {
        RoutingPolicy policy = registry.policy(ReviewDomain.ARCHITECTURE_REVIEW).orElseThrow();
        assertThat(policy.threshold().getAsDouble()).isEqualTo(0.65);
    }

    @Test
    void architectureReviewMinimumObservationsIs8() {
        RoutingPolicy policy = registry.policy(ReviewDomain.ARCHITECTURE_REVIEW).orElseThrow();
        assertThat(policy.minimumObservations().getAsInt()).isEqualTo(8);
    }

    @Test
    void styleReviewThresholdIs050() {
        RoutingPolicy policy = registry.policy(ReviewDomain.STYLE_REVIEW).orElseThrow();
        assertThat(policy.threshold().getAsDouble()).isEqualTo(0.50);
    }

    @Test
    void styleReviewMinimumObservationsIs5() {
        RoutingPolicy policy = registry.policy(ReviewDomain.STYLE_REVIEW).orElseThrow();
        assertThat(policy.minimumObservations().getAsInt()).isEqualTo(5);
    }

    @Test
    void styleReviewHasNoBorderlineMargin() {
        RoutingPolicy policy = registry.policy(ReviewDomain.STYLE_REVIEW).orElseThrow();
        assertThat(policy.borderlineMargin()).isEmpty();
    }

    @Test
    void mergeExecutorThresholdIs080() {
        RoutingPolicy policy = registry.policy(AgentQualification.MERGE_EXECUTOR).orElseThrow();
        assertThat(policy.threshold().getAsDouble()).isEqualTo(0.80);
    }

    @Test
    void mergeExecutorMinimumObservationsIs15() {
        RoutingPolicy policy = registry.policy(AgentQualification.MERGE_EXECUTOR).orElseThrow();
        assertThat(policy.minimumObservations().getAsInt()).isEqualTo(15);
    }

    @Test
    void mergeExecutorBorderlineMarginIs005() {
        RoutingPolicy policy = registry.policy(AgentQualification.MERGE_EXECUTOR).orElseThrow();
        assertThat(policy.borderlineMargin().getAsDouble()).isEqualTo(0.05);
    }

    @Test
    void architectureReviewBorderlineMarginIs005() {
        RoutingPolicy policy = registry.policy(ReviewDomain.ARCHITECTURE_REVIEW).orElseThrow();
        assertThat(policy.borderlineMargin().getAsDouble()).isEqualTo(0.05);
    }

    @Test
    void architectureReviewFallbackIsHumanOversightRoutingReview() {
        RoutingPolicy policy = registry.policy(ReviewDomain.ARCHITECTURE_REVIEW).orElseThrow();
        assertThat(policy.fallbackType()).contains(HumanOversight.ROUTING_REVIEW);
    }

    @Test
    void styleReviewHasNoFallbackType() {
        RoutingPolicy policy = registry.policy(ReviewDomain.STYLE_REVIEW).orElseThrow();
        assertThat(policy.fallbackType()).isEmpty();
    }

    @Test
    void mergeExecutorFallbackIsHumanOversightRoutingReview() {
        RoutingPolicy policy = registry.policy(AgentQualification.MERGE_EXECUTOR).orElseThrow();
        assertThat(policy.fallbackType()).contains(HumanOversight.ROUTING_REVIEW);
    }

    // === policy() — non-gated capabilities return empty ===

    @Test
    void codeAnalysisHasNoPolicy() {
        assertThat(registry.policy(ReviewDomain.CODE_ANALYSIS)).isEmpty();
    }

    @Test
    void testCoverageHasNoPolicy() {
        assertThat(registry.policy(ReviewDomain.TEST_COVERAGE)).isEmpty();
    }

    @Test
    void performanceAnalysisHasNoPolicy() {
        assertThat(registry.policy(ReviewDomain.PERFORMANCE_ANALYSIS)).isEmpty();
    }

    @Test
    void ciRunnerHasNoPolicy() {
        assertThat(registry.policy(AgentQualification.CI_RUNNER)).isEmpty();
    }

    @Test
    void humanDecisionPrApprovalHasNoPolicy() {
        assertThat(registry.policy(HumanDecision.PR_APPROVAL)).isEmpty();
    }

    @Test
    void humanOversightRoutingReviewHasNoPolicy() {
        assertThat(registry.policy(HumanOversight.ROUTING_REVIEW)).isEmpty();
    }

    // === All threshold values in valid range ===

    @Test
    void allThresholdValuesAreInValidRange() {
        registry.capabilities().stream()
            .map(registry::policy)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(RoutingPolicy::threshold)
            .filter(OptionalDouble::isPresent)
            .mapToDouble(OptionalDouble::getAsDouble)
            .forEach(t -> assertThat(t)
                .as("threshold must be in [0.0, 1.0]")
                .isGreaterThanOrEqualTo(0.0)
                .isLessThanOrEqualTo(1.0));
    }

    // === isKnown() ===

    @Test
    void isKnownReturnsTrueForAllCapabilities() {
        assertThat(registry.isKnown(ReviewDomain.SECURITY_REVIEW)).isTrue();
        assertThat(registry.isKnown(AgentQualification.MERGE_EXECUTOR)).isTrue();
        assertThat(registry.isKnown(HumanDecision.PR_APPROVAL)).isTrue();
        assertThat(registry.isKnown(HumanOversight.ROUTING_REVIEW)).isTrue();
    }

    @Test
    void isKnownReturnsFalseForUnknownCapability() {
        assertThat(registry.isKnown("unknown-capability")).isFalse();
        assertThat(registry.isKnown("")).isFalse();
    }

    // === Null guards ===

    @Test
    void policyNullThrowsNullPointerException() {
        assertThatThrownBy(() -> registry.policy(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("capability");
    }

    @Test
    void isKnownNullThrowsNullPointerException() {
        assertThatThrownBy(() -> registry.isKnown(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("capability");
    }

    // === Maturity model integration ===

    @Test
    void securityReviewPolicyAgentWithFewObservationsIsInBootstrap() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.isBootstrap(4)).isTrue();
        assertThat(policy.isBootstrap(10)).isFalse();
    }

    @Test
    void securityReviewPolicyAgentJustAboveThresholdIsBorderline() {
        RoutingPolicy policy = registry.policy(ReviewDomain.SECURITY_REVIEW).orElseThrow();
        assertThat(policy.isBorderline(0.71)).isTrue();
        assertThat(policy.isBorderline(0.75)).isFalse();
    }

    // === Implements CapabilityRegistry SPI ===

    @Test
    void implementsCapabilityRegistrySpi() {
        assertThat(registry).isInstanceOf(CapabilityRegistry.class);
    }
}
