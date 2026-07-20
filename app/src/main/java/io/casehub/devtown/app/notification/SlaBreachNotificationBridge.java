package io.casehub.devtown.app.notification;

import io.casehub.devtown.domain.notification.NotificationPreferenceKeys;
import io.casehub.devtown.review.notification.SlaEscalatedEvent;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.runtime.event.SlaBreachEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SlaBreachNotificationBridge {

    private final Event<SlaEscalatedEvent> slaEscalatedEvents;

    @Inject
    public SlaBreachNotificationBridge(Event<SlaEscalatedEvent> slaEscalatedEvents) {
        this.slaEscalatedEvents = slaEscalatedEvents;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onSlaBreach(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            SlaBreachEvent event) {
        if (!(event.decision() instanceof BreachDecision.EscalateTo escalation)) return;
        BreachedTask task = event.context().task();
        String targetChannel = event.context().preferences()
            .getOrDefault(NotificationPreferenceKeys.SLACK_CHANNEL).value();
        slaEscalatedEvents.fire(new SlaEscalatedEvent(
            task.taskId().toString(),
            task.title(),
            task.callerRef(),
            event.context().breachType().name(),
            String.join(", ", escalation.groups()),
            event.context().scope().toString(),
            targetChannel,
            event.tenancyId()));
    }
}
