package io.casehub.devtown.app.governance;

import io.casehub.devtown.app.governance.GovernanceQueryService.TimelineCategory;
import io.casehub.devtown.app.governance.GovernanceQueryService.TimelineEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimelineEventTest {

    @Test
    void timelineEventRecordShape() {
        var event = new TimelineEvent(
                Instant.parse("2026-10-06T14:00:00Z"),
                TimelineCategory.LIFECYCLE,
                "CASE_STARTED",
                "system",
                "Case started",
                "{\"status\":\"STARTING\"}");

        assertEquals(TimelineCategory.LIFECYCLE, event.category());
        assertEquals("CASE_STARTED", event.eventType());
        assertEquals("system", event.actor());
        assertEquals("Case started", event.summary());
        assertNotNull(event.metadata());
    }

    @Test
    void timelineEventNullMetadata() {
        var event = new TimelineEvent(
                Instant.now(), TimelineCategory.AGENT,
                "WORKER_SCHEDULED", "system", "scheduled", null);

        assertNull(event.metadata());
    }

    @ParameterizedTest
    @CsvSource({
            "LIFECYCLE, LIFECYCLE",
            "ORCHESTRATION, ORCHESTRATION",
            "AGENT, AGENT",
            "WORKITEM, WORKITEM",
            "TRUST, TRUST",
            "CI, CI",
            "SIGNAL, SIGNAL"
    })
    void timelineCategoryValues(String name, String expected) {
        assertEquals(TimelineCategory.valueOf(expected), TimelineCategory.valueOf(name));
    }

    @Test
    void timelineCategoryCount() {
        assertEquals(7, TimelineCategory.values().length);
    }

    @Test
    void routingDecisionRecord() {
        var decision = new GovernanceQueryService.RoutingDecision(
                "security-review", "auth paths detected", 0.85, "initial-analysis");

        assertEquals("security-review", decision.capability());
        assertEquals("auth paths detected", decision.reason());
        assertEquals(0.85, decision.confidence());
        assertEquals("initial-analysis", decision.bindingName());
    }

    @Test
    void routingSummaryRecord() {
        var summary = new GovernanceQueryService.RoutingSummary(
                java.util.List.of(), "{\"securitySensitive\":true}");

        assertTrue(summary.decisions().isEmpty());
        assertNotNull(summary.featureVector());
    }

    @Test
    void findingEntryRecord() {
        var finding = new GovernanceQueryService.FindingEntry(
                "HIGH", "credential-exposure", "src/auth/Token.java",
                "Hardcoded secret", 0.9, 42, 45);

        assertEquals("HIGH", finding.severity());
        assertEquals("src/auth/Token.java", finding.filePath());
        assertEquals(42, finding.startLine());
        assertEquals(45, finding.endLine());
    }

    @Test
    void findingEntryNullLines() {
        var finding = new GovernanceQueryService.FindingEntry(
                "LOW", "naming", "src/Foo.java",
                "bad name", 0.6, null, null);

        assertNull(finding.startLine());
        assertNull(finding.endLine());
    }

    @Test
    void reviewDetailRecordShape() {
        var detail = new GovernanceQueryService.ReviewDetail(
                java.util.UUID.randomUUID(),
                new io.casehub.devtown.review.PrPayload("org/repo", 42, "sha", "main", 100, "dev", 1L, java.util.List.of()),
                java.util.List.of(),
                java.util.List.of(),
                new GovernanceQueryService.RoutingSummary(java.util.List.of(), null),
                java.util.Map.of());

        assertNotNull(detail.routing());
        assertNotNull(detail.findings());
        assertTrue(detail.timeline().isEmpty());
        assertTrue(detail.capabilities().isEmpty());
    }
}
