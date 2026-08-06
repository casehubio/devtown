package io.casehub.devtown.app.governance;

import io.casehub.api.model.TaskStatus;
import io.casehub.engine.common.spi.event.CaseContextUpdatedEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.event.PlanItemStateChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GovernanceEventBridgeTest {

    @Test
    void onCaseLifecycle_sendsJsonToConnectedSessions() throws Exception {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.isOpen()).thenReturn(true);

        bridge.onOpen(session);

        var event = CaseLifecycleEvent.of(
            UUID.randomUUID(), "default", "CASE_COMPLETED", "CASE_STATE_CHANGED",
            "COMPLETED", "actor-1", "user", "trace-123"
        );
        bridge.onCaseLifecycle(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote).sendText(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"op\":\"event\"");
        assertThat(json).contains("\"topic\":\"case.state\"");
        assertThat(json).contains("COMPLETED");
    }

    @Test
    void onClose_removesSession() {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.isOpen()).thenReturn(true);

        bridge.onOpen(session);
        bridge.onClose(session);

        // Fire an event — session should NOT receive it
        var event = CaseLifecycleEvent.of(
            UUID.randomUUID(), "default", "CASE_COMPLETED", "CASE_STATE_CHANGED",
            "COMPLETED", "actor-1", "user", "trace-123"
        );
        bridge.onCaseLifecycle(event);

        verifyNoInteractions(asyncRemote);
    }

    @Test
    void onError_removesSession() {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.getId()).thenReturn("session-1");

        bridge.onOpen(session);
        bridge.onError(session, new RuntimeException("test error"));

        // Fire an event — session should NOT receive it
        when(session.isOpen()).thenReturn(false);
        var event = CaseLifecycleEvent.of(
            UUID.randomUUID(), "default", "CASE_COMPLETED", "CASE_STATE_CHANGED",
            "COMPLETED", "actor-1", "user", "trace-123"
        );
        bridge.onCaseLifecycle(event);

        verifyNoInteractions(asyncRemote);
    }

    @Test
    void onPlanItemChanged_sendsJsonToConnectedSessions() {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.isOpen()).thenReturn(true);

        bridge.onOpen(session);

        var event = new PlanItemStateChangedEvent(
            UUID.randomUUID(), "pi-style", "style-check",
            TaskStatus.RUNNING, TaskStatus.COMPLETED, "test-tenant");
        bridge.onPlanItemChanged(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote).sendText(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"op\":\"event\"");
        assertThat(json).contains("\"topic\":\"planitem.state\"");
        assertThat(json).contains("pi-style");
        assertThat(json).contains("style-check");
        assertThat(json).contains("COMPLETED");
        assertThat(json).contains("RUNNING");
    }

    @Test
    void onContextUpdated_sendsJsonToConnectedSessions() {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.isOpen()).thenReturn(true);

        bridge.onOpen(session);

        var event = new CaseContextUpdatedEvent(
            UUID.randomUUID(), "analysis", "test-tenant");
        bridge.onContextUpdated(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote).sendText(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"op\":\"event\"");
        assertThat(json).contains("\"topic\":\"context.update\"");
        assertThat(json).contains("analysis");
    }

    @Test
    void onPlanItemChanged_handlesNullPreviousStatus() {
        var bridge = new GovernanceEventBridge();
        var session = mock(Session.class);
        var asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        when(session.isOpen()).thenReturn(true);

        bridge.onOpen(session);

        var event = new PlanItemStateChangedEvent(
            UUID.randomUUID(), "pi-new", "initial-analysis",
            null, TaskStatus.PENDING, "test-tenant");
        bridge.onPlanItemChanged(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote).sendText(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"topic\":\"planitem.state\"");
        assertThat(json).contains("PENDING");
    }
}
