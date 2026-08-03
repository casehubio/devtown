package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewerTrustDimensionTest {

    @Test
    void allConstantsNonBlank() {
        assertThat(ReviewerTrustDimension.REVIEW_THOROUGHNESS).isNotBlank();
        assertThat(ReviewerTrustDimension.PRECISION).isNotBlank();
        assertThat(ReviewerTrustDimension.SCOPE_CALIBRATION).isNotBlank();
        assertThat(ReviewerTrustDimension.RESPONSIVENESS).isNotBlank();
    }

    @Test
    void allConstantsUnique() {
        assertThat(Set.of(
                ReviewerTrustDimension.REVIEW_THOROUGHNESS,
                ReviewerTrustDimension.PRECISION,
                ReviewerTrustDimension.SCOPE_CALIBRATION,
                ReviewerTrustDimension.RESPONSIVENESS
        )).hasSize(4);
    }

    @Test
    void valuesMatchSpec() {
        assertThat(ReviewerTrustDimension.REVIEW_THOROUGHNESS).isEqualTo("review-thoroughness");
        assertThat(ReviewerTrustDimension.PRECISION).isEqualTo("precision");
        assertThat(ReviewerTrustDimension.SCOPE_CALIBRATION).isEqualTo("scope-calibration");
        assertThat(ReviewerTrustDimension.RESPONSIVENESS).isEqualTo("responsiveness");
    }
}
