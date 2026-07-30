package io.casehub.devtown.app.notification;

import io.casehub.devtown.review.notification.AgentReviewDispatchedEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AgentDispatchNotificationBridge implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(AgentDispatchNotificationBridge.class);

    @Inject
    Event<AgentReviewDispatchedEvent> dispatchedEvents;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.messageType() != MessageType.COMMAND) return;
        if (event.target() == null) return;

        try {
            dispatchedEvents.fire(new AgentReviewDispatchedEvent(
                    event.target(),
                    extractCapability(event.content()),
                    event.channelName(),
                    event.tenancyId()));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fire agent dispatch notification for target=%s channel=%s",
                    event.target(), event.channelName());
        }
    }

    private static String extractCapability(String content) {
        if (content == null) return null;
        int idx = content.indexOf("\"capability\"");
        if (idx < 0) return null;
        int colon = content.indexOf(':', idx);
        if (colon < 0) return null;
        int quote1 = content.indexOf('"', colon + 1);
        if (quote1 < 0) return null;
        int quote2 = content.indexOf('"', quote1 + 1);
        if (quote2 < 0) return null;
        return content.substring(quote1 + 1, quote2);
    }
}
