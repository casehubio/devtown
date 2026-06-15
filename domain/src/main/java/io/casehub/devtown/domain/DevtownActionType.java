package io.casehub.devtown.domain;

public final class DevtownActionType {

    public static final String PR_MERGE_EXECUTE = "pr-merge-execute";
    public static final String PR_FORCE_MERGE = "pr-force-merge";
    public static final String PR_REVIEW_OVERRIDE = "pr-review-override";
    public static final String SECURITY_ESCALATION = "security-escalation";
    public static final String ISSUE_CLOSE_INVALID = "issue-close-invalid";
    public static final String DEPENDENCY_REMOVAL = "dependency-removal";
    public static final String CONTRIBUTOR_ACCESS_CHANGE = "contributor-access-change";
    public static final String PRODUCTION_DEPLOY = "production-deploy";

    private DevtownActionType() {}
}
