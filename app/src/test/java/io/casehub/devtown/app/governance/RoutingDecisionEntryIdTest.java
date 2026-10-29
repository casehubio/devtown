package io.casehub.devtown.app.governance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoutingDecisionEntryIdTest {

    @Test
    void routingDecision_includesEntryId() {
        UUID id = UUID.randomUUID();
        var rd = new GovernanceQueryService.RoutingDecision(
            "security-review", "trust-weighted", 0.85, "SecurityBinding", id
        );
        assertEquals(id, rd.entryId());
        assertEquals("security-review", rd.capability());
    }

    @Test
    void routingDecision_entryIdNullForInProgress() {
        var rd = new GovernanceQueryService.RoutingDecision(
            "code-analysis", "direct", 1.0, "AnalysisBinding", null
        );
        assertNull(rd.entryId());
    }
}
