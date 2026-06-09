package io.casehub.devtown.review.compliance;

import java.util.List;

public record TrustRoutingRequirement(
    String requirementId,
    String citation,
    String mechanism,
    RequirementStatus status,
    List<RoutingDecisionRecord> decisions
) {
    public static final String REQUIREMENT_ID = "trust-routing";
    public static final String CITATION = "EU AI Act Art.14 — Human Oversight of AI Routing";
    public static final String MECHANISM = "TrustWeightedAgentStrategy with capability-scoped thresholds";
}
