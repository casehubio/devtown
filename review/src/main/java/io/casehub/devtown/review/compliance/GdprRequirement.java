package io.casehub.devtown.review.compliance;

public record GdprRequirement(
    String requirementId,
    String citation,
    String mechanism,
    RequirementStatus status,
    boolean erasureCapabilityWired,
    boolean pseudonymisationActive
) {
    public static final String REQUIREMENT_ID = "gdpr";
    public static final String CITATION = "GDPR Art.17 Right to Erasure, Art.22 Automated Decision Records";
    public static final String MECHANISM = "LedgerErasureService + ActorIdentityProvider tokenisation";
}
