package io.casehub.devtown.domain;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailurePolicyTest {

    @Test
    void scopeReductionEnabled_holdsAllFields() {
        var policy = new FailurePolicy(true, "{ flaggedFiles: .codeAnalysis.flaggedFiles }", Duration.ofHours(4));
        assertThat(policy.scopeReductionAllowed()).isTrue();
        assertThat(policy.reducedInputSchema()).isEqualTo("{ flaggedFiles: .codeAnalysis.flaggedFiles }");
        assertThat(policy.humanEscalationSla()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void scopeReductionDisabled_nullInputSchema() {
        var policy = new FailurePolicy(false, null, Duration.ofHours(2));
        assertThat(policy.scopeReductionAllowed()).isFalse();
        assertThat(policy.reducedInputSchema()).isNull();
        assertThat(policy.humanEscalationSla()).isEqualTo(Duration.ofHours(2));
    }
}
