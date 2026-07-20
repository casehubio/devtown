package io.casehub.devtown.review.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscribableEventTest {

    @Test
    void reviewAssignedEventType() {
        var e = new ReviewAssignedEvent("wi-1", "user-a", "#ops", "t1");
        assertEquals("io.casehub.devtown.review.assigned", e.type());
        assertEquals("t1", e.tenancyId());
    }

    @Test
    void mergeSucceededEventType() {
        var e = new MergeSucceededEvent("case-1", "pr-review", "42", "2",
            "PR #42, PR #43", "system", "#ops", "t1");
        assertEquals("io.casehub.devtown.merge.succeeded", e.type());
    }

    @Test
    void mergeFailedEventType() {
        var e = new MergeFailedEvent("42", "Fix auth", "user-b", "Bob",
            "CI timeout", "repo-1", "/pr/42", "#ops", "t1");
        assertEquals("io.casehub.devtown.merge.failed", e.type());
    }

    @Test
    void stalledCommitmentEventType() {
        var e = new StalledCommitmentEvent("OBLIGATION_FAN_OUT", "channel-1",
            "2 obligations unresponded", "2026-07-20T10:00:00Z", "system", "#ops", "t1");
        assertEquals("io.casehub.devtown.commitment.stalled", e.type());
    }

    @Test
    void caseFaultedEventType() {
        var e = new CaseFaultedEvent("case-1", "pr-review-v1", "FAULTED",
            null, "#ops", "t1");
        assertEquals("io.casehub.devtown.case.faulted", e.type());
    }

    @Test
    void slaEscalatedEventType() {
        var e = new SlaEscalatedEvent("task-1", "Review PR #42", "devtown:pr-review",
            "COMPLETION", "pr-leads", "/repos/my-repo", "#ops", "t1");
        assertEquals("io.casehub.devtown.sla.escalated", e.type());
    }

    @Test
    void allEventsPropagateTenancyId() {
        String tenancy = "tenant-42";
        assertEquals(tenancy, new ReviewAssignedEvent("w", "u", "#c", tenancy).tenancyId());
        assertEquals(tenancy, new MergeSucceededEvent("c", "d", "p", "n", "l", "a", "#c", tenancy).tenancyId());
        assertEquals(tenancy, new MergeFailedEvent("p", "t", "a", "n", "r", "repo", "u", "#c", tenancy).tenancyId());
        assertEquals(tenancy, new StalledCommitmentEvent("ct", "tn", "s", "f", "a", "#c", tenancy).tenancyId());
        assertEquals(tenancy, new CaseFaultedEvent("c", "d", "s", null, "#c", tenancy).tenancyId());
        assertEquals(tenancy, new SlaEscalatedEvent("t", "ti", "c", "b", "g", "s", "#c", tenancy).tenancyId());
    }
}
