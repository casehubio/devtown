package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.devtown.review.notification.SlaEscalatedEvent;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlaBreachNotificationBridgeTest {

    private SlaBreachNotificationBridge bridge;
    private List<SlaEscalatedEvent> fired;

    @BeforeEach
    void setUp() {
        fired = new ArrayList<>();
        bridge = new SlaBreachNotificationBridge(capturingEvent(fired));
    }

    @Test
    void escalateToDecisionFiresEvent() {
        Preferences prefs = mock(Preferences.class);
        when(prefs.getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL))
            .thenReturn(StringPreference.of("#escalations"));
        BreachedTask task = new BreachedTask(UUID.randomUUID(), "devtown:pr-review",
            "Review PR #42", Set.of("pr-leads"));
        SlaBreachContext ctx = new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task,
            Path.of("my-repo"), prefs);
        BreachDecision.EscalateTo decision = BreachDecision.EscalateTo.to("pr-leads", "managers");
        SlaBreachEvent event = new SlaBreachEvent(ctx, decision, "tenant-1");

        bridge.onSlaBreach(event);

        assertEquals(1, fired.size());
        SlaEscalatedEvent e = fired.getFirst();
        assertEquals("io.casehub.devtown.sla.escalated", e.type());
        assertEquals(task.taskId().toString(), e.taskId());
        assertEquals("Review PR #42", e.taskTitle());
        assertEquals("COMPLETION_EXPIRED", e.breachType());
        assertTrue(e.escalationGroups().contains("pr-leads"));
        assertEquals("#escalations", e.targetChannel());
        assertEquals("tenant-1", e.tenancyId());
    }

    @Test
    void failDecisionIsIgnored() {
        Preferences prefs = mock(Preferences.class);
        BreachedTask task = new BreachedTask(UUID.randomUUID(), "devtown:pr-review",
            "Review PR #42", Set.of());
        SlaBreachContext ctx = new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task,
            Path.of("my-repo"), prefs);
        BreachDecision.Fail decision = new BreachDecision.Fail("timeout");
        SlaBreachEvent event = new SlaBreachEvent(ctx, decision, "tenant-1");

        bridge.onSlaBreach(event);
        assertTrue(fired.isEmpty());
    }

    @Test
    void extendDecisionIsIgnored() {
        Preferences prefs = mock(Preferences.class);
        BreachedTask task = new BreachedTask(UUID.randomUUID(), "devtown:pr-review",
            "Review PR #42", Set.of());
        SlaBreachContext ctx = new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task,
            Path.of("my-repo"), prefs);
        BreachDecision.Extend decision = new BreachDecision.Extend(java.time.Duration.ofHours(2));
        SlaBreachEvent event = new SlaBreachEvent(ctx, decision, "tenant-1");

        bridge.onSlaBreach(event);
        assertTrue(fired.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private <T> Event<T> capturingEvent(List<T> captured) {
        Event<T> event = mock(Event.class);
        doAnswer(inv -> { captured.add(inv.getArgument(0)); return null; })
            .when(event).fire(any());
        return event;
    }
}
