package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.devtown.review.notification.ReviewAssignedEvent;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemType;
import io.casehub.work.api.WorkItemStatus;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewAssignmentNotificationBridgeTest {

    private ReviewAssignmentNotificationBridge bridge;
    private List<ReviewAssignedEvent> fired;

    @BeforeEach
    void setUp() {
        fired = new ArrayList<>();
        PreferenceProvider preferenceProvider = mock(PreferenceProvider.class);
        Preferences prefs = mock(Preferences.class);
        when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
        when(prefs.getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL))
            .thenReturn(StringPreference.of("#reviews"));
        bridge = new ReviewAssignmentNotificationBridge(capturingEvent(fired), preferenceProvider);
    }

    @Test
    void createdWorkItemWithPrApprovalTypeFiresEvent() {
        WorkItemLifecycleEvent event = workItemEvent("created",
            Set.of("human-decision:pr-approval"), "reviewer-1");
        bridge.onWorkItemCreated(event);
        assertEquals(1, fired.size());
        assertEquals("reviewer-1", fired.getFirst().assigneeId());
        assertEquals("#reviews", fired.getFirst().targetChannel());
        assertEquals("io.casehub.devtown.review.assigned", fired.getFirst().type());
    }

    @Test
    void createdWorkItemWithoutPrApprovalTypeIsIgnored() {
        WorkItemLifecycleEvent event = workItemEvent("created",
            Set.of("human-oversight:routing-review"), "reviewer-1");
        bridge.onWorkItemCreated(event);
        assertTrue(fired.isEmpty());
    }

    @Test
    void nonCreatedEventIsIgnored() {
        WorkItemLifecycleEvent event = workItemEvent("completed",
            Set.of("human-decision:pr-approval"), "reviewer-1");
        bridge.onWorkItemCreated(event);
        assertTrue(fired.isEmpty());
    }

    private WorkItemLifecycleEvent workItemEvent(String eventName, Set<String> typeStrings, String assigneeId) {
        WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.tenancyId = "tenant-1";
        wi.assigneeId = assigneeId;
        wi.callerRef = "devtown:pr-review";
        wi.status = WorkItemStatus.PENDING;
        wi.scope = "my-repo";
        wi.types = new LinkedHashSet<>();
        for (String t : typeStrings) {
            WorkItemType wit = new WorkItemType();
            wit.path = t;
            wi.types.add(wit);
        }
        return WorkItemLifecycleEvent.of(eventName, wi, "system", null);
    }

    @SuppressWarnings("unchecked")
    private <T> Event<T> capturingEvent(List<T> captured) {
        Event<T> event = mock(Event.class);
        doAnswer(inv -> { captured.add(inv.getArgument(0)); return null; })
            .when(event).fire(any());
        return event;
    }
}
