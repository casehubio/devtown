package io.casehub.devtown.domain;

import java.util.Objects;

public final class ContributorTrustCapability {
    public static final String PR_CONTRIBUTION = "pr-contribution";
    public static final String DOMAIN_SEPARATOR = ":";

    public static String forDomain(String reviewDomain) {
        Objects.requireNonNull(reviewDomain, "reviewDomain must not be null");
        if (!ReviewDomain.FINDINGS_CAPABILITIES.contains(reviewDomain)) {
            throw new IllegalArgumentException("Unknown review domain: " + reviewDomain);
        }
        return PR_CONTRIBUTION + DOMAIN_SEPARATOR + reviewDomain;
    }

    public static boolean isDomainScoped(String capabilityTag) {
        return capabilityTag != null && capabilityTag.startsWith(PR_CONTRIBUTION + DOMAIN_SEPARATOR);
    }

    private ContributorTrustCapability() {}
}
