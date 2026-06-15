package io.casehub.devtown.domain.preferences;

import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class RiskPreferenceKeys {

    private static final String NS = "casehubio.devtown.risk";

    public static final PreferenceKey<BooleanPreference> ENABLED =
            new PreferenceKey<>(NS, "enabled", BooleanPreference.of(true), BooleanPreference::parse);

    public static final PreferenceKey<IntPreference> EXPIRES_IN_MINUTES_REVERSIBLE =
            new PreferenceKey<>(NS, "expiresInMinutes", IntPreference.of(240), IntPreference::parse);

    public static final PreferenceKey<IntPreference> EXPIRES_IN_MINUTES_IRREVERSIBLE =
            new PreferenceKey<>(NS, "expiresInMinutes", IntPreference.of(1440), IntPreference::parse);

    public static final PreferenceKey<IntPreference> MERGE_MIN_APPROVED_REVIEWS =
            new PreferenceKey<>(NS, "threshold", IntPreference.of(1), IntPreference::parse);

    public static final PreferenceKey<StringPreference> SECURITY_SEVERITY_THRESHOLD =
            new PreferenceKey<>(NS, "threshold", StringPreference.of("HIGH"), StringPreference::parse);

    public static final PreferenceKey<IntPreference> ISSUE_CLOSE_COMMENT_THRESHOLD =
            new PreferenceKey<>(NS, "threshold", IntPreference.of(5), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DEPENDENCY_USAGE_THRESHOLD =
            new PreferenceKey<>(NS, "threshold", IntPreference.of(3), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DEPLOY_MODULE_THRESHOLD =
            new PreferenceKey<>(NS, "threshold", IntPreference.of(3), IntPreference::parse);

    private RiskPreferenceKeys() {}
}
