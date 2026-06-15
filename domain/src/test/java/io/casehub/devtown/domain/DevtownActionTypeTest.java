package io.casehub.devtown.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevtownActionTypeTest {

    @Test
    void allConstantsNonBlank() {
        assertThat(DevtownActionType.PR_MERGE_EXECUTE).isNotBlank();
        assertThat(DevtownActionType.PR_FORCE_MERGE).isNotBlank();
        assertThat(DevtownActionType.PR_REVIEW_OVERRIDE).isNotBlank();
        assertThat(DevtownActionType.SECURITY_ESCALATION).isNotBlank();
        assertThat(DevtownActionType.ISSUE_CLOSE_INVALID).isNotBlank();
        assertThat(DevtownActionType.DEPENDENCY_REMOVAL).isNotBlank();
        assertThat(DevtownActionType.CONTRIBUTOR_ACCESS_CHANGE).isNotBlank();
        assertThat(DevtownActionType.PRODUCTION_DEPLOY).isNotBlank();
    }

    @Test
    void valuesMatchSpec() {
        assertThat(DevtownActionType.PR_MERGE_EXECUTE).isEqualTo("pr-merge-execute");
        assertThat(DevtownActionType.PR_FORCE_MERGE).isEqualTo("pr-force-merge");
        assertThat(DevtownActionType.PR_REVIEW_OVERRIDE).isEqualTo("pr-review-override");
        assertThat(DevtownActionType.SECURITY_ESCALATION).isEqualTo("security-escalation");
        assertThat(DevtownActionType.ISSUE_CLOSE_INVALID).isEqualTo("issue-close-invalid");
        assertThat(DevtownActionType.DEPENDENCY_REMOVAL).isEqualTo("dependency-removal");
        assertThat(DevtownActionType.CONTRIBUTOR_ACCESS_CHANGE).isEqualTo("contributor-access-change");
        assertThat(DevtownActionType.PRODUCTION_DEPLOY).isEqualTo("production-deploy");
    }

    @Test
    void noOverlapWithHumanDecisionOrOversight() {
        String[] types = {
            DevtownActionType.PR_MERGE_EXECUTE,
            DevtownActionType.PR_FORCE_MERGE,
            DevtownActionType.PR_REVIEW_OVERRIDE,
            DevtownActionType.SECURITY_ESCALATION,
            DevtownActionType.ISSUE_CLOSE_INVALID,
            DevtownActionType.DEPENDENCY_REMOVAL,
            DevtownActionType.CONTRIBUTOR_ACCESS_CHANGE,
            DevtownActionType.PRODUCTION_DEPLOY,
        };
        for (final String type : types) {
            assertThat(type).doesNotStartWith("human-decision:");
            assertThat(type).doesNotStartWith("human-oversight:");
        }
    }
}
