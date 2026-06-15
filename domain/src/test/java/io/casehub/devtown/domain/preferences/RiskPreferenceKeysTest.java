package io.casehub.devtown.domain.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskPreferenceKeysTest {

    private static final String NAMESPACE = "casehubio.devtown.risk";

    @Test
    void enabled_hasCorrectNamespaceAndName() {
        assertThat(RiskPreferenceKeys.ENABLED.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.ENABLED.name()).isEqualTo("enabled");
    }

    @Test
    void enabled_defaultIsTrue() {
        assertThat(RiskPreferenceKeys.ENABLED.defaultValue().value()).isTrue();
    }

    @Test
    void expiresInMinutes_reversible_defaultIs240() {
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_REVERSIBLE.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_REVERSIBLE.name()).isEqualTo("expiresInMinutes");
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_REVERSIBLE.defaultValue().value()).isEqualTo(240);
    }

    @Test
    void expiresInMinutes_irreversible_defaultIs1440() {
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_IRREVERSIBLE.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_IRREVERSIBLE.name()).isEqualTo("expiresInMinutes");
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_IRREVERSIBLE.defaultValue().value()).isEqualTo(1440);
    }

    @Test
    void expiresInMinutes_bothKeysShareSameName() {
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_REVERSIBLE.name())
            .isEqualTo(RiskPreferenceKeys.EXPIRES_IN_MINUTES_IRREVERSIBLE.name());
    }

    @Test
    void mergeMinApprovedReviews() {
        assertThat(RiskPreferenceKeys.MERGE_MIN_APPROVED_REVIEWS.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.MERGE_MIN_APPROVED_REVIEWS.name()).isEqualTo("threshold");
        assertThat(RiskPreferenceKeys.MERGE_MIN_APPROVED_REVIEWS.defaultValue().value()).isEqualTo(1);
    }

    @Test
    void securitySeverityThreshold() {
        assertThat(RiskPreferenceKeys.SECURITY_SEVERITY_THRESHOLD.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.SECURITY_SEVERITY_THRESHOLD.name()).isEqualTo("threshold");
        assertThat(RiskPreferenceKeys.SECURITY_SEVERITY_THRESHOLD.defaultValue().value()).isEqualTo("HIGH");
    }

    @Test
    void issueCloseCommentThreshold() {
        assertThat(RiskPreferenceKeys.ISSUE_CLOSE_COMMENT_THRESHOLD.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.ISSUE_CLOSE_COMMENT_THRESHOLD.name()).isEqualTo("threshold");
        assertThat(RiskPreferenceKeys.ISSUE_CLOSE_COMMENT_THRESHOLD.defaultValue().value()).isEqualTo(5);
    }

    @Test
    void dependencyUsageThreshold() {
        assertThat(RiskPreferenceKeys.DEPENDENCY_USAGE_THRESHOLD.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.DEPENDENCY_USAGE_THRESHOLD.name()).isEqualTo("threshold");
        assertThat(RiskPreferenceKeys.DEPENDENCY_USAGE_THRESHOLD.defaultValue().value()).isEqualTo(3);
    }

    @Test
    void deployModuleThreshold() {
        assertThat(RiskPreferenceKeys.DEPLOY_MODULE_THRESHOLD.namespace()).isEqualTo(NAMESPACE);
        assertThat(RiskPreferenceKeys.DEPLOY_MODULE_THRESHOLD.name()).isEqualTo("threshold");
        assertThat(RiskPreferenceKeys.DEPLOY_MODULE_THRESHOLD.defaultValue().value()).isEqualTo(3);
    }

    @Test
    void allKeysHaveParsers() {
        assertThat(RiskPreferenceKeys.ENABLED.parse("false").value()).isFalse();
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_REVERSIBLE.parse("60").value()).isEqualTo(60);
        assertThat(RiskPreferenceKeys.EXPIRES_IN_MINUTES_IRREVERSIBLE.parse("120").value()).isEqualTo(120);
        assertThat(RiskPreferenceKeys.MERGE_MIN_APPROVED_REVIEWS.parse("2").value()).isEqualTo(2);
        assertThat(RiskPreferenceKeys.SECURITY_SEVERITY_THRESHOLD.parse("CRITICAL").value()).isEqualTo("CRITICAL");
        assertThat(RiskPreferenceKeys.ISSUE_CLOSE_COMMENT_THRESHOLD.parse("10").value()).isEqualTo(10);
        assertThat(RiskPreferenceKeys.DEPENDENCY_USAGE_THRESHOLD.parse("5").value()).isEqualTo(5);
        assertThat(RiskPreferenceKeys.DEPLOY_MODULE_THRESHOLD.parse("1").value()).isEqualTo(1);
    }
}
