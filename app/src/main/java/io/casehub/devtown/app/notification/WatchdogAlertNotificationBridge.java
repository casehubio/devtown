package io.casehub.devtown.app.notification;

import io.casehub.devtown.review.notification.StalledCommitmentEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Set;

@ApplicationScoped
public class WatchdogAlertNotificationBridge {

    private static final Set<WatchdogConditionType> REVIEWER_CONDITIONS = Set.of(
        WatchdogConditionType.OBLIGATION_FAN_OUT,
        WatchdogConditionType.CONVERSATION_STALL);

    private final Event<StalledCommitmentEvent> stalledEvents;

    @Inject
    public WatchdogAlertNotificationBridge(Event<StalledCommitmentEvent> stalledEvents) {
        this.stalledEvents = stalledEvents;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void onWatchdogAlert(@ObservesAsync WatchdogAlertEvent event) {
        if (!REVIEWER_CONDITIONS.contains(event.conditionType())) return;
        stalledEvents.fire(new StalledCommitmentEvent(
            event.conditionType().name(),
            event.targetName(),
            event.summary(),
            event.firedAt().toString(),
            "system",
            event.notificationChannel(),
            null));
    }
}
