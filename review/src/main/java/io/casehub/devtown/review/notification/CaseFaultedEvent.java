package io.casehub.devtown.review.notification;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record CaseFaultedEvent(
    String caseId,
    String caseDefinitionName,
    String caseStatus,
    String contextSnapshot,
    String targetChannel,
    String tenancyId
) implements SubscribableEvent {
    @Override public String type() { return "io.casehub.devtown.case.faulted"; }
    @Override public String tenancyId() { return tenancyId; }
}
