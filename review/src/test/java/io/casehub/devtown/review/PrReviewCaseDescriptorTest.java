package io.casehub.devtown.review;

import io.casehub.devtown.domain.FailurePolicy;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.AgentQualification;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PrReviewCaseDescriptorTest {

    @Test
    void securityReview_hasScopeReduction() {
        FailurePolicy policy = PrReviewCaseDescriptor.FAILURE_POLICIES.get(ReviewDomain.SECURITY_REVIEW);
        assertThat(policy).isNotNull();
        assertThat(policy.scopeReductionAllowed()).isTrue();
        assertThat(policy.reducedInputSchema()).isNotNull();
        assertThat(policy.humanEscalationSla()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void architectureReview_hasScopeReduction() {
        FailurePolicy policy = PrReviewCaseDescriptor.FAILURE_POLICIES.get(ReviewDomain.ARCHITECTURE_REVIEW);
        assertThat(policy).isNotNull();
        assertThat(policy.scopeReductionAllowed()).isTrue();
        assertThat(policy.reducedInputSchema()).isNotNull();
    }

    @Test
    void styleReview_noScopeReduction() {
        FailurePolicy policy = PrReviewCaseDescriptor.FAILURE_POLICIES.get(ReviewDomain.STYLE_REVIEW);
        assertThat(policy).isNotNull();
        assertThat(policy.scopeReductionAllowed()).isFalse();
        assertThat(policy.reducedInputSchema()).isNull();
        assertThat(policy.humanEscalationSla()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void ciRunner_noScopeReduction_shorterSla() {
        FailurePolicy policy = PrReviewCaseDescriptor.FAILURE_POLICIES.get(AgentQualification.CI_RUNNER);
        assertThat(policy).isNotNull();
        assertThat(policy.scopeReductionAllowed()).isFalse();
        assertThat(policy.humanEscalationSla()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void allCapabilities_havePolicy() {
        assertThat(PrReviewCaseDescriptor.FAILURE_POLICIES)
            .containsKeys(
                ReviewDomain.SECURITY_REVIEW,
                ReviewDomain.ARCHITECTURE_REVIEW,
                ReviewDomain.STYLE_REVIEW,
                ReviewDomain.TEST_COVERAGE,
                ReviewDomain.PERFORMANCE_ANALYSIS,
                ReviewDomain.CODE_ANALYSIS,
                AgentQualification.CI_RUNNER);
    }
}
