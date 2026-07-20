package io.casehub.devtown.app.notification;

import io.casehub.devtown.review.notification.StalledCommitmentEvent;
import io.casehub.qhorus.api.watchdog.BarrierStuckContext;
import io.casehub.qhorus.api.watchdog.ConversationStallContext;
import io.casehub.qhorus.api.watchdog.ObligationFanOutContext;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WatchdogAlertNotificationBridgeTest {

    private WatchdogAlertNotificationBridge bridge;
    private List<StalledCommitmentEvent> fired;

    @BeforeEach
    void setUp() {
        fired = new ArrayList<>();
        bridge = new WatchdogAlertNotificationBridge(capturingEvent(fired));
    }

    @Test
    void obligationFanOutFiresStalledEvent() {
        var ctx = new ObligationFanOutContext(UUID.randomUUID(), "channel-1", 2, List.of("c1", "c2"));
        bridge.onWatchdogAlert(new WatchdogAlertEvent(UUID.randomUUID(), "channel-1", "notif-channel",
            "2 obligations unresponded", Instant.parse("2026-07-20T10:00:00Z"), ctx));
        assertEquals(1, fired.size());
        assertEquals("OBLIGATION_FAN_OUT", fired.getFirst().conditionType());
        assertEquals("channel-1", fired.getFirst().targetName());
        assertEquals("io.casehub.devtown.commitment.stalled", fired.getFirst().type());
    }

    @Test
    void conversationStallFiresStalledEvent() {
        var ctx = new ConversationStallContext(UUID.randomUUID(), "channel-2", 3, List.of("c1"), 120);
        bridge.onWatchdogAlert(new WatchdogAlertEvent(UUID.randomUUID(), "channel-2", "notif-channel",
            "3 stalled conversations", Instant.parse("2026-07-20T10:00:00Z"), ctx));
        assertEquals(1, fired.size());
        assertEquals("CONVERSATION_STALL", fired.getFirst().conditionType());
    }

    @Test
    void barrierStuckIsIgnored() {
        var ctx = new BarrierStuckContext(UUID.randomUUID(), "channel-x", List.of("a"), 60);
        bridge.onWatchdogAlert(new WatchdogAlertEvent(UUID.randomUUID(), "channel-x", "notif-channel",
            "barrier stuck", Instant.parse("2026-07-20T10:00:00Z"), ctx));
        assertTrue(fired.isEmpty(), "Should not fire for BARRIER_STUCK");
    }

    @Test
    void summaryAndFiredAtPropagated() {
        var ctx = new ObligationFanOutContext(UUID.randomUUID(), "ch-1", 2, List.of("c1"));
        bridge.onWatchdogAlert(new WatchdogAlertEvent(UUID.randomUUID(), "ch-1", "notif-channel",
            "2 obligations unresponded", Instant.parse("2026-07-20T10:00:00Z"), ctx));
        assertEquals("2 obligations unresponded", fired.getFirst().summary());
        assertEquals("2026-07-20T10:00:00Z", fired.getFirst().firedAt());
    }

    @SuppressWarnings("unchecked")
    private <T> Event<T> capturingEvent(List<T> captured) {
        Event<T> event = mock(Event.class);
        doAnswer(inv -> { captured.add(inv.getArgument(0)); return null; })
            .when(event).fire(any());
        return event;
    }
}
