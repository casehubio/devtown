package io.casehub.devtown.domain.notification;

import io.casehub.devtown.domain.sla.StringPreference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferenceKeysTest {

    @Test
    void slackChannelHasCorrectQualifiedName() {
        assertEquals("devtown.notification.slack-channel",
            NotificationPreferenceKeys.SLACK_CHANNEL.qualifiedName());
    }

    @Test
    void slackChannelDefaultIsDevtownOps() {
        assertEquals("#devtown-ops",
            NotificationPreferenceKeys.SLACK_CHANNEL.defaultValue().value());
    }

    @Test
    void slackChannelParsesStringValue() {
        StringPreference parsed = NotificationPreferenceKeys.SLACK_CHANNEL.parse("#custom-channel");
        assertEquals("#custom-channel", parsed.value());
    }

    @Test
    void teamsChannelHasCorrectQualifiedName() {
        assertEquals("devtown.notification.teams-channel",
            NotificationPreferenceKeys.TEAMS_CHANNEL.qualifiedName());
    }

    @Test
    void teamsChannelDefaultIsEmpty() {
        assertEquals("", NotificationPreferenceKeys.TEAMS_CHANNEL.defaultValue().value());
    }
}
