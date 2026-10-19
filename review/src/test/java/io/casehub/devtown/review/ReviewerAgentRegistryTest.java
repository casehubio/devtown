package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewerAgentRegistryTest {

    @Test
    void forCapability_returnsAgent() {
        var agent = stubAgent("security-review");
        var registry = ReviewerAgentRegistry.of(List.of(agent));
        assertTrue(registry.forCapability("security-review").isPresent());
        assertSame(agent, registry.forCapability("security-review").get());
    }

    @Test
    void forCapability_returnsEmpty_forUnknown() {
        var registry = ReviewerAgentRegistry.of(List.of(stubAgent("security-review")));
        assertTrue(registry.forCapability("nonexistent").isEmpty());
    }

    @Test
    void all_returnsAllAgents() {
        var registry = ReviewerAgentRegistry.of(List.of(
            stubAgent("security-review"), stubAgent("style-review")));
        assertEquals(2, registry.all().size());
    }

    @Test
    void duplicateCapability_throws() {
        assertThrows(IllegalStateException.class, () ->
            ReviewerAgentRegistry.of(List.of(
                stubAgent("security-review"), stubAgent("security-review"))));
    }

    private static ReviewerAgent stubAgent(String capability) {
        return new ReviewerAgent() {
            @Override public String capability() { return capability; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Declined("stub");
            }
        };
    }
}
