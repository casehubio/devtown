package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record AgentReviewDispatchedEvent(
    String agentId,
    String capability,
    String channelName,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.review.agent-dispatched"; }
    @Override public String tenancyId() { return tenancyId; }
}
