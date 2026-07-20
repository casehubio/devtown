package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record SlaEscalatedEvent(
    String taskId,
    String taskTitle,
    String callerRef,
    String breachType,
    String escalationGroups,
    String scope,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.sla.escalated"; }
    @Override public String tenancyId() { return tenancyId; }
}
