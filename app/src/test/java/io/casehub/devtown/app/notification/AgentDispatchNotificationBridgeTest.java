package io.casehub.devtown.app.notification;

import io.casehub.devtown.review.notification.AgentReviewDispatchedEvent;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentDispatchNotificationBridgeTest {

    private Event<AgentReviewDispatchedEvent> eventBus;
    private AgentDispatchNotificationBridge bridge;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        eventBus = mock(Event.class);
        bridge = new AgentDispatchNotificationBridge();
        bridge.dispatchedEvents = eventBus;
    }

    @Test
    void command_with_target_fires_event() {
        var event = new MessageReceivedEvent(
                1L, "work-channel", UUID.randomUUID(), "tenant-1",
                MessageType.COMMAND, "coordinator", "agent-alice", null,
                "corr-1", Instant.now(),
                "{\"capability\":\"code-analysis\"}", null);

        bridge.onMessage(event);

        var captor = ArgumentCaptor.forClass(AgentReviewDispatchedEvent.class);
        verify(eventBus).fire(captor.capture());
        var fired = captor.getValue();
        assertThat(fired.agentId()).isEqualTo("agent-alice");
        assertThat(fired.capability()).isEqualTo("code-analysis");
        assertThat(fired.channelName()).isEqualTo("work-channel");
        assertThat(fired.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void non_command_is_skipped() {
        var event = new MessageReceivedEvent(
                1L, "ch", UUID.randomUUID(), "t1",
                MessageType.STATUS, "agent", null, null,
                null, Instant.now(), "working", null);

        bridge.onMessage(event);

        verify(eventBus, never()).fire(any());
    }

    @Test
    void command_without_target_is_skipped() {
        var event = new MessageReceivedEvent(
                1L, "ch", UUID.randomUUID(), "t1",
                MessageType.COMMAND, "coordinator", null, null,
                "corr-1", Instant.now(), "{}", null);

        bridge.onMessage(event);

        verify(eventBus, never()).fire(any());
    }

    @Test
    void null_content_yields_null_capability() {
        var event = new MessageReceivedEvent(
                1L, "ch", UUID.randomUUID(), "t1",
                MessageType.COMMAND, "coordinator", "agent-bob", null,
                "corr-1", Instant.now(), null, null);

        bridge.onMessage(event);

        var captor = ArgumentCaptor.forClass(AgentReviewDispatchedEvent.class);
        verify(eventBus).fire(captor.capture());
        assertThat(captor.getValue().capability()).isNull();
    }
}
