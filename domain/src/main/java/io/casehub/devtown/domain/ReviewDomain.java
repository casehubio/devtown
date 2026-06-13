package io.casehub.devtown.domain;

import java.util.Set;

public final class ReviewDomain {

    public static final String CODE_ANALYSIS        = "code-analysis";
    public static final String SECURITY_REVIEW      = "security-review";
    public static final String ARCHITECTURE_REVIEW  = "architecture-review";
    public static final String STYLE_REVIEW         = "style-review";
    public static final String TEST_COVERAGE        = "test-coverage";
    public static final String PERFORMANCE_ANALYSIS = "performance-analysis";

    public static final Set<String> REVIEW_CAPABILITIES = Set.of(
        CODE_ANALYSIS, SECURITY_REVIEW, ARCHITECTURE_REVIEW,
        STYLE_REVIEW, TEST_COVERAGE, PERFORMANCE_ANALYSIS
    );

    private ReviewDomain() {}
}
