package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DevtownTrustDimensionTest {

    @Test
    void allConstantsNonBlank() {
        assertThat(DevtownTrustDimension.REVIEW_THOROUGHNESS).isNotBlank();
        assertThat(DevtownTrustDimension.FALSE_POSITIVE_RATE).isNotBlank();
        assertThat(DevtownTrustDimension.SCOPE_CALIBRATION).isNotBlank();
    }

    @Test
    void allConstantsUnique() {
        assertThat(Set.of(
            DevtownTrustDimension.REVIEW_THOROUGHNESS,
            DevtownTrustDimension.FALSE_POSITIVE_RATE,
            DevtownTrustDimension.SCOPE_CALIBRATION
        )).hasSize(3);
    }

    @Test
    void valuesMatchSpec() {
        assertThat(DevtownTrustDimension.REVIEW_THOROUGHNESS).isEqualTo("review-thoroughness");
        assertThat(DevtownTrustDimension.FALSE_POSITIVE_RATE).isEqualTo("false-positive-rate");
        assertThat(DevtownTrustDimension.SCOPE_CALIBRATION).isEqualTo("scope-calibration");
    }
}
