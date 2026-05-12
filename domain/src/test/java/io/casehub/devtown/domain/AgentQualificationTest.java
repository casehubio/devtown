package io.casehub.devtown.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AgentQualificationTest {

    @Test
    void allConstantsNonBlank() {
        assertThat(AgentQualification.CI_RUNNER).isNotBlank();
        assertThat(AgentQualification.MERGE_EXECUTOR).isNotBlank();
    }

    @Test
    void allConstantsUnique() {
        assertThat(Set.of(
            AgentQualification.CI_RUNNER,
            AgentQualification.MERGE_EXECUTOR
        )).hasSize(2);
    }

    @Test
    void valuesMatchSpec() {
        assertThat(AgentQualification.CI_RUNNER).isEqualTo("ci-runner");
        assertThat(AgentQualification.MERGE_EXECUTOR).isEqualTo("merge-executor");
    }

    @Test
    void noOverlapWithReviewDomain() {
        assertThat(AgentQualification.CI_RUNNER).doesNotStartWith("code-")
            .doesNotStartWith("security-").doesNotStartWith("architecture-")
            .doesNotStartWith("style-").doesNotStartWith("test-")
            .doesNotStartWith("performance-");
        assertThat(AgentQualification.MERGE_EXECUTOR).doesNotStartWith("code-")
            .doesNotStartWith("security-").doesNotStartWith("architecture-")
            .doesNotStartWith("style-").doesNotStartWith("test-")
            .doesNotStartWith("performance-");
    }
}
