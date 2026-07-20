package io.casehub.devtown.app.notification;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DevtownSubscriptionRegistrarTest {

    private CapturingSubscriptionStore store;
    private DevtownSubscriptionRegistrar registrar;

    @BeforeEach
    void setUp() {
        store = new CapturingSubscriptionStore();
        registrar = new DevtownSubscriptionRegistrar(store);
    }

    @Test
    void registersAllSixSubscriptions() {
        registrar.register("default-tenant");
        assertEquals(6, store.stored.size());
    }

    @Test
    void mergeFailedSubscriptionHasCorrectShape() {
        registrar.register("default-tenant");
        SubscriptionInput mergeFailed = store.stored.stream()
            .filter(s -> s.name().equals("devtown-merge-failed"))
            .findFirst().orElseThrow();
        assertEquals("io.casehub.devtown.merge.failed", mergeFailed.eventType());
        assertEquals(NotificationSeverity.URGENT, mergeFailed.template().severity());
        assertEquals("devtown.merge", mergeFailed.template().entityType());
        assertEquals(SubscriptionScope.SYSTEM, mergeFailed.scope());
        assertTrue(mergeFailed.targets().stream()
            .anyMatch(t -> t.type() == TargetType.GROUP && t.id().equals("devtown-ops")));
        assertTrue(mergeFailed.targets().stream()
            .anyMatch(t -> t.type() == TargetType.EVENT_FIELD && t.id().equals("authorId")));
    }

    @Test
    void reviewAssignedSubscriptionHasInfoSeverity() {
        registrar.register("default-tenant");
        SubscriptionInput reviewAssigned = store.stored.stream()
            .filter(s -> s.name().equals("devtown-review-assigned"))
            .findFirst().orElseThrow();
        assertEquals(NotificationSeverity.INFO, reviewAssigned.template().severity());
        assertEquals("io.casehub.devtown.review.assigned", reviewAssigned.eventType());
    }

    @Test
    void slaEscalatedSubscriptionUsesCallerRefAsActorId() {
        registrar.register("default-tenant");
        SubscriptionInput sla = store.stored.stream()
            .filter(s -> s.name().equals("devtown-sla-escalated"))
            .findFirst().orElseThrow();
        assertEquals("callerRef", sla.template().actorIdField());
        assertEquals("taskId", sla.template().entityIdField());
    }

    @Test
    void idempotentRegistration() {
        registrar.register("default-tenant");
        registrar.register("default-tenant");
        assertEquals(6, store.stored.size(), "Second registration should be no-op");
    }

    @Test
    void allTemplatesHaveRequiredFields() {
        registrar.register("default-tenant");
        for (SubscriptionInput sub : store.stored) {
            NotificationTemplate t = sub.template();
            assertNotNull(t.titlePattern(), sub.name() + " titlePattern");
            assertNotNull(t.severity(), sub.name() + " severity");
            assertNotNull(t.category(), sub.name() + " category");
            assertNotNull(t.entityType(), sub.name() + " entityType");
            assertNotNull(t.entityIdField(), sub.name() + " entityIdField");
            assertNotNull(t.actorIdField(), sub.name() + " actorIdField");
        }
    }

    @Test
    void allSubscriptionsAreSystemScope() {
        registrar.register("default-tenant");
        for (SubscriptionInput sub : store.stored) {
            assertEquals(SubscriptionScope.SYSTEM, sub.scope(), sub.name() + " should be SYSTEM scope");
        }
    }

    static class CapturingSubscriptionStore implements SubscriptionStore {
        final List<SubscriptionInput> stored = new CopyOnWriteArrayList<>();

        @Override
        public Subscription store(SubscriptionInput input) {
            stored.add(input);
            return toSubscription(input);
        }

        @Override
        public SubscriptionPage find(SubscriptionQuery query) {
            List<Subscription> matches = stored.stream().map(this::toSubscription).toList();
            return new SubscriptionPage(matches, null);
        }

        @Override public Optional<Subscription> findById(String id, String ownerId, String tenancyId) { return Optional.empty(); }
        @Override public Optional<Subscription> update(String id, String ownerId, String tenancyId, SubscriptionUpdate update) { return Optional.empty(); }
        @Override public boolean delete(String id, String ownerId, String tenancyId) { return false; }
        @Override public Stream<Subscription> findAllEnabled() { return stored.stream().map(this::toSubscription); }

        private Subscription toSubscription(SubscriptionInput i) {
            return new Subscription("sub-" + stored.size(), i.ownerId(), i.tenancyId(), i.name(),
                i.eventType(), i.filters(), i.targets(), i.includeActor(),
                i.template(), i.enabled(), i.scope(), Instant.now(), Instant.now());
        }
    }
}
