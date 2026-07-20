package io.casehub.devtown.domain.notification;

import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class NotificationPreferenceKeys {

    public static final PreferenceKey<StringPreference> SLACK_CHANNEL =
        new PreferenceKey<>("devtown.notification", "slack-channel",
            StringPreference.of("#devtown-ops"), StringPreference::parse);

    public static final PreferenceKey<StringPreference> TEAMS_CHANNEL =
        new PreferenceKey<>("devtown.notification", "teams-channel",
            StringPreference.of(""), StringPreference::parse);

    private NotificationPreferenceKeys() {}
}
