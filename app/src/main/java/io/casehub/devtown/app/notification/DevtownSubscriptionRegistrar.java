package io.casehub.devtown.app.notification;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.TargetType;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DevtownSubscriptionRegistrar {

    private final SubscriptionStore subscriptionStore;

    @Inject
    public DevtownSubscriptionRegistrar(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    void onStartup(@Observes StartupEvent event) {
        register("default");
    }

    void register(String tenancyId) {
        SubscriptionPage existing = subscriptionStore.find(new SubscriptionQuery(
            null, tenancyId, SubscriptionScope.SYSTEM, null, null, 100));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-review-assigned",
            "io.casehub.devtown.review.assigned", List.of(),
            List.of(new NotificationTarget(TargetType.EVENT_FIELD, "assigneeId")),
            false,
            new NotificationTemplate("PR #{prNumber} assigned for {capability} review",
                "{prTitle} by {authorName} — deadline {deadline}",
                NotificationSeverity.INFO, "devtown.review.assigned",
                "/api/workitems/{workItemId}", "devtown.review", "prNumber", "assigneeId"),
            true, SubscriptionScope.SYSTEM));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-merge-succeeded",
            "io.casehub.devtown.merge.succeeded", List.of(),
            List.of(new NotificationTarget(TargetType.ENTITY_WATCHERS, "")),
            false,
            new NotificationTemplate("Batch merged: {prCount} PRs", "{prList}",
                NotificationSeverity.INFO, "devtown.merge.succeeded",
                "/api/reviews/{prNumber}", "devtown.merge", "prNumber", "actorId"),
            true, SubscriptionScope.SYSTEM));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-merge-failed",
            "io.casehub.devtown.merge.failed", List.of(),
            List.of(
                new NotificationTarget(TargetType.GROUP, "devtown-ops"),
                new NotificationTarget(TargetType.EVENT_FIELD, "authorId")),
            false,
            new NotificationTemplate("Merge rejected: {prTitle}",
                "CI failure: {failureReason} — author: {authorName}",
                NotificationSeverity.URGENT, "devtown.merge.failed",
                "/api/reviews/{prNumber}", "devtown.merge", "prNumber", "authorId"),
            true, SubscriptionScope.SYSTEM));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-commitment-stalled",
            "io.casehub.devtown.commitment.stalled", List.of(),
            List.of(new NotificationTarget(TargetType.GROUP, "devtown-ops")),
            false,
            new NotificationTemplate("Stalled: {conditionType} on {targetName}",
                "{summary} — fired at {firedAt}",
                NotificationSeverity.WARNING, "devtown.commitment.stalled",
                null, "devtown.watchdog", "targetName", "actorId"),
            true, SubscriptionScope.SYSTEM));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-case-faulted",
            "io.casehub.devtown.case.faulted", List.of(),
            List.of(new NotificationTarget(TargetType.GROUP, "devtown-ops")),
            false,
            new NotificationTemplate("Case faulted: {caseDefinitionName}",
                "Case {caseId} in state {caseStatus}",
                NotificationSeverity.URGENT, "devtown.case.faulted",
                "/api/compliance/code-review/{caseId}", "devtown.case", "caseId", "actorId"),
            true, SubscriptionScope.SYSTEM));

        registerIfAbsent(existing, tenancyId, new SubscriptionInput(
            "system:devtown", tenancyId, "devtown-sla-escalated",
            "io.casehub.devtown.sla.escalated", List.of(),
            List.of(new NotificationTarget(TargetType.GROUP, "devtown-ops")),
            false,
            new NotificationTemplate("SLA breach: {taskTitle}",
                "{breachType} — escalated to {escalationGroups}",
                NotificationSeverity.URGENT, "devtown.sla.escalated",
                "/api/workitems/{taskId}", "devtown.sla", "taskId", "callerRef"),
            true, SubscriptionScope.SYSTEM));
    }

    private void registerIfAbsent(SubscriptionPage existing, String tenancyId, SubscriptionInput input) {
        boolean alreadyExists = existing.subscriptions().stream()
            .anyMatch(s -> s.name().equals(input.name()));
        if (!alreadyExists) {
            subscriptionStore.store(input);
        }
    }
}
