package io.casehub.devtown.domain.sla;

import io.casehub.platform.api.preferences.PreferenceKey;

public final class SlaPreferenceKeys {

    public static final PreferenceKey<IntPreference> ESCALATION_HOURS =
        new PreferenceKey<>("devtown.sla", "escalation-hours",
            IntPreference.of(8), IntPreference::parse);

    public static final PreferenceKey<StringPreference> ESCALATION_GROUP =
        new PreferenceKey<>("devtown.sla", "escalation-group",
            StringPreference.of("pr-leads"), StringPreference::parse);

    public static final PreferenceKey<StringPreference> BREACH_TERMINAL_REASON =
        new PreferenceKey<>("devtown.sla", "breach-terminal-reason",
            StringPreference.of("sla-breach"), StringPreference::parse);

    public static final PreferenceKey<IntPreference> COMPLETION_HOURS =
        new PreferenceKey<>("devtown.sla", "completion-hours",
            IntPreference.of(24), IntPreference::parse);

    public static final PreferenceKey<StringPreference> CANDIDATE_GROUP =
        new PreferenceKey<>("devtown.sla", "candidate-group",
            StringPreference.of("pr-reviewers"), StringPreference::parse);

    private SlaPreferenceKeys() {}
}
