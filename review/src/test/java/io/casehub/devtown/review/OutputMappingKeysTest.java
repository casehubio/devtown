package io.casehub.devtown.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputMappingKeysTest {

    @Test
    void extractsSimpleKey() {
        assertThat(OutputMappingKeys.topLevelKey("{ humanApproval: . }"))
                .isEqualTo("humanApproval");
    }

    @Test
    void extractsNestedKey() {
        assertThat(OutputMappingKeys.topLevelKey("{ securityReview: { outcome: . } }"))
                .isEqualTo("securityReview");
    }

    @Test
    void extractsKeyWithWhitespaceVariations() {
        assertThat(OutputMappingKeys.topLevelKey("{styleCheck: .}"))
                .isEqualTo("styleCheck");
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(OutputMappingKeys.topLevelKey(null)).isNull();
    }

    @Test
    void returnsNullForDotOnly() {
        assertThat(OutputMappingKeys.topLevelKey(".")).isNull();
    }

    @Test
    void extractsKeyWithQuotedField() {
        assertThat(OutputMappingKeys.topLevelKey("{ \"performanceAnalysis\": { outcome: . } }"))
                .isEqualTo("performanceAnalysis");
    }
}
