package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewFinding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReviewerAgentRegistryTest {

    @Test
    void highestPriorityWinsPerCapability() {
        ReviewerAgent stub = new ReviewerAgent() {
            @Override public String capability() { return ReviewDomain.SECURITY_REVIEW; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Completed(List.of());
            }
        };

        ReviewerAgent llm = new ReviewerAgent() {
            @Override public String capability() { return ReviewDomain.SECURITY_REVIEW; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Completed(List.of(
                    new ReviewFinding(ReviewFinding.Severity.HIGH, "injection",
                        "src/A.java", null, "found issue", 0.9)));
            }
            @Override public int priority() { return 1; }
        };

        var registry = ReviewerAgentRegistry.of(List.of(stub, llm));
        var resolved = registry.forCapability(ReviewDomain.SECURITY_REVIEW);
        assertTrue(resolved.isPresent());
        assertEquals(1, resolved.get().priority());
    }

    @Test
    void missingCapabilityReturnsEmpty() {
        var registry = ReviewerAgentRegistry.of(List.of());
        assertTrue(registry.forCapability("nonexistent").isEmpty());
    }

    @Test
    void stubWinsWhenNoLlmAgent() {
        ReviewerAgent stub = new ReviewerAgent() {
            @Override public String capability() { return ReviewDomain.STYLE_REVIEW; }
            @Override public ReviewerOutcome handle(ReviewContext ctx) {
                return new ReviewerOutcome.Completed(List.of());
            }
        };

        var registry = ReviewerAgentRegistry.of(List.of(stub));
        var resolved = registry.forCapability(ReviewDomain.STYLE_REVIEW);
        assertTrue(resolved.isPresent());
        assertEquals(0, resolved.get().priority());
    }
}
