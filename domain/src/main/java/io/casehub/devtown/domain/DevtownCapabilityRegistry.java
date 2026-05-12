package io.casehub.devtown.domain;

import io.casehub.devtown.domain.spi.CapabilityRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public class DevtownCapabilityRegistry implements CapabilityRegistry {

    private static final Set<String> ALL_CAPABILITIES = Set.of(
        ReviewDomain.CODE_ANALYSIS,
        ReviewDomain.SECURITY_REVIEW,
        ReviewDomain.ARCHITECTURE_REVIEW,
        ReviewDomain.STYLE_REVIEW,
        ReviewDomain.TEST_COVERAGE,
        ReviewDomain.PERFORMANCE_ANALYSIS,
        AgentQualification.CI_RUNNER,
        AgentQualification.MERGE_EXECUTOR,
        HumanDecision.PR_APPROVAL,
        HumanOversight.ROUTING_REVIEW
    );

    private static final Map<String, RoutingPolicy> POLICIES = Map.of(
        ReviewDomain.SECURITY_REVIEW, new RoutingPolicy(
            OptionalDouble.of(0.70),
            OptionalInt.of(10),
            OptionalDouble.of(0.05),
            Optional.of(HumanOversight.ROUTING_REVIEW),
            "Security mistakes reach production; 10 observations required for credible score"
        ),
        ReviewDomain.ARCHITECTURE_REVIEW, new RoutingPolicy(
            OptionalDouble.of(0.65),
            OptionalInt.of(8),
            OptionalDouble.of(0.05),
            Optional.of(HumanOversight.ROUTING_REVIEW),
            "Design mistakes are expensive to reverse; 8 observations required"
        ),
        ReviewDomain.STYLE_REVIEW, new RoutingPolicy(
            OptionalDouble.of(0.50),
            OptionalInt.of(5),
            OptionalDouble.empty(),
            Optional.empty(),
            "Baseline — any competent agent; 5 observations sufficient"
        ),
        AgentQualification.MERGE_EXECUTOR, new RoutingPolicy(
            OptionalDouble.of(0.80),
            OptionalInt.of(15),
            OptionalDouble.of(0.05),
            Optional.of(HumanOversight.ROUTING_REVIEW),
            "Merge is irreversible; highest observation requirement"
        )
    );

    @Override
    public Set<String> capabilities() {
        return ALL_CAPABILITIES;
    }

    @Override
    public Optional<RoutingPolicy> policy(String capability) {
        Objects.requireNonNull(capability, "capability must not be null");
        return Optional.ofNullable(POLICIES.get(capability));
    }
}
