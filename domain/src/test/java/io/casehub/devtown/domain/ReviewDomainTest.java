package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewDomainTest {

    @Test
    void allConstantsNonBlank() {
        assertThat(ReviewDomain.CODE_ANALYSIS).isNotBlank();
        assertThat(ReviewDomain.SECURITY_REVIEW).isNotBlank();
        assertThat(ReviewDomain.ARCHITECTURE_REVIEW).isNotBlank();
        assertThat(ReviewDomain.STYLE_REVIEW).isNotBlank();
        assertThat(ReviewDomain.TEST_COVERAGE).isNotBlank();
        assertThat(ReviewDomain.PERFORMANCE_ANALYSIS).isNotBlank();
    }

    @Test
    void allConstantsUnique() {
        assertThat(Set.of(
            ReviewDomain.CODE_ANALYSIS,
            ReviewDomain.SECURITY_REVIEW,
            ReviewDomain.ARCHITECTURE_REVIEW,
            ReviewDomain.STYLE_REVIEW,
            ReviewDomain.TEST_COVERAGE,
            ReviewDomain.PERFORMANCE_ANALYSIS
        )).hasSize(6);
    }

    @Test
    void valuesMatchSpec() {
        assertThat(ReviewDomain.CODE_ANALYSIS).isEqualTo("code-analysis");
        assertThat(ReviewDomain.SECURITY_REVIEW).isEqualTo("security-review");
        assertThat(ReviewDomain.ARCHITECTURE_REVIEW).isEqualTo("architecture-review");
        assertThat(ReviewDomain.STYLE_REVIEW).isEqualTo("style-review");
        assertThat(ReviewDomain.TEST_COVERAGE).isEqualTo("test-coverage");
        assertThat(ReviewDomain.PERFORMANCE_ANALYSIS).isEqualTo("performance-analysis");
    }
}
