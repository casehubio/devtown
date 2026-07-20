package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.devtown.review.notification.CaseFaultedEvent;
import io.casehub.devtown.review.notification.MergeFailedEvent;
import io.casehub.devtown.review.notification.MergeSucceededEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CaseLifecycleNotificationBridgeTest {

    private CaseLifecycleNotificationBridge bridge;
    private List<MergeSucceededEvent> mergeSucceededFired;
    private List<MergeFailedEvent> mergeFailedFired;
    private List<CaseFaultedEvent> caseFaultedFired;

    @BeforeEach
    void setUp() {
        mergeSucceededFired = new ArrayList<>();
        mergeFailedFired = new ArrayList<>();
        caseFaultedFired = new ArrayList<>();
        PreferenceProvider preferenceProvider = mock(PreferenceProvider.class);
        Preferences prefs = mock(Preferences.class);
        when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
        when(prefs.getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL))
            .thenReturn(StringPreference.of("#test-channel"));
        bridge = new CaseLifecycleNotificationBridge(
            capturingEvent(mergeSucceededFired),
            capturingEvent(mergeFailedFired),
            capturingEvent(caseFaultedFired),
            preferenceProvider);
    }

    @Test
    void completedCaseFiresMergeSucceeded() {
        bridge.onCaseLifecycle(caseEvent("COMPLETED", "pr-review-v1", "ns1"));
        assertEquals(1, mergeSucceededFired.size());
        assertEquals("io.casehub.devtown.merge.succeeded", mergeSucceededFired.getFirst().type());
        assertEquals("#test-channel", mergeSucceededFired.getFirst().targetChannel());
        assertEquals("tenant-1", mergeSucceededFired.getFirst().tenancyId());
    }

    @Test
    void cancelledCaseFiresMergeFailed() {
        bridge.onCaseLifecycle(caseEvent("CANCELLED", "pr-review-v1", "ns1"));
        assertEquals(1, mergeFailedFired.size());
        assertEquals("io.casehub.devtown.merge.failed", mergeFailedFired.getFirst().type());
    }

    @Test
    void faultedCaseFiresCaseFaulted() {
        bridge.onCaseLifecycle(caseEvent("FAULTED", "pr-review-v1", "ns1"));
        assertEquals(1, caseFaultedFired.size());
        assertEquals("io.casehub.devtown.case.faulted", caseFaultedFired.getFirst().type());
        assertEquals("pr-review-v1", caseFaultedFired.getFirst().caseDefinitionName());
    }

    @Test
    void otherStatusesAreIgnored() {
        bridge.onCaseLifecycle(caseEvent("SUSPENDED", "pr-review-v1", "ns1"));
        assertTrue(mergeSucceededFired.isEmpty());
        assertTrue(mergeFailedFired.isEmpty());
        assertTrue(caseFaultedFired.isEmpty());
    }

    @Test
    void nullStatusIsIgnored() {
        bridge.onCaseLifecycle(caseEvent(null, "pr-review-v1", "ns1"));
        assertTrue(mergeSucceededFired.isEmpty());
        assertTrue(mergeFailedFired.isEmpty());
        assertTrue(caseFaultedFired.isEmpty());
    }

    private CaseLifecycleEvent caseEvent(String status, String defName, String namespace) {
        return new CaseLifecycleEvent(
            UUID.randomUUID(), "tenant-1", "CompleteCase", "CaseCompleted",
            status, "system", "ORCHESTRATOR", null,
            defName, namespace, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private <T> Event<T> capturingEvent(List<T> captured) {
        Event<T> event = mock(Event.class);
        doAnswer(inv -> { captured.add(inv.getArgument(0)); return null; })
            .when(event).fire(any());
        return event;
    }
}
