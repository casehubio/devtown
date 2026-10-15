package io.casehub.devtown.app;

import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.casehub.devtown.domain.queue.PriorityLane;
import io.casehub.devtown.merge.MergeQueuePort;
import io.casehub.devtown.queue.QueuedPr;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DevModeDataSeeder {

    private static final Logger LOG = Logger.getLogger(DevModeDataSeeder.class);

    @Inject PrReviewApplicationService reviewService;
    @Inject PrReviewCaseTracker caseTracker;
    @Inject MergeQueuePort mergeQueue;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject MergeQueueService mergeQueueService;

    @ConfigProperty(name = "devtown.dev-seeder.enabled", defaultValue = "false")
    boolean enabled;

    void onStartup(@Observes StartupEvent event) {
        if (!enabled) return;
        if (!caseTracker.activeCases().isEmpty()) {
            LOG.info("DevModeDataSeeder: cases already exist, skipping");
            return;
        }

        LOG.info("DevModeDataSeeder: seeding dev-mode data");
        seedTrustScores();
        seedPrReviewCases();
        seedMergeQueue();
        LOG.infof("DevModeDataSeeder: seeded %d cases, trust scores for 11 actors, 5 merge queue entries",
                caseTracker.activeCases().size());
    }

    private void seedTrustScores() {
        seedAgentTrust("agent-code-01", 0.85, "code-analysis", 0.85, 47, "style-review", 0.92, 30);
        seedAgentTrust("agent-sec-01", 0.71, "security-review", 0.71, 32, "code-analysis", 0.68, 20);
        seedAgentTrust("agent-arch-01", 0.65, "architecture-review", 0.65, 12);
        seedAgentTrust("agent-perf-01", 0.78, "performance-analysis", 0.78, 21);
        seedAgentTrust("agent-test-01", 0.88, "test-coverage", 0.88, 55);
        seedAgentTrust("agent-style-01", 0.95, "style-review", 0.95, 3);

        seedContributorTrust("alice", 0.88, 34, 0.94, 0.82);
        seedContributorTrust("bob", 0.72, 18, 0.78, 0.65);
        seedContributorTrust("carol", 0.45, 8, 0.50, 0.38);
        seedContributorTrust("dave", 0.91, 52, 0.96, 0.90);
        seedContributorTrust("eve", 0.63, 11, 0.73, 0.55);
    }

    private void seedAgentTrust(String actorId, double globalScore, Object... capTriplets) {
        int totalDecisions = 0;
        for (int i = 2; i < capTriplets.length; i += 3) {
            totalDecisions += ((Number) capTriplets[i]).intValue();
        }

        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
                ActorType.AGENT, globalScore, totalDecisions, 0,
                globalScore * 10, (1 - globalScore) * 10,
                (int) (globalScore * totalDecisions), (int) ((1 - globalScore) * totalDecisions),
                Instant.now());

        for (int i = 0; i < capTriplets.length; i += 3) {
            String cap = (String) capTriplets[i];
            double score = ((Number) capTriplets[i + 1]).doubleValue();
            int decisions = ((Number) capTriplets[i + 2]).intValue();
            trustRepo.upsert(actorId, ScoreType.CAPABILITY, cap, null,
                    ActorType.AGENT, score, decisions, 0,
                    score * 10, (1 - score) * 10,
                    (int) (score * decisions), (int) ((1 - score) * decisions),
                    Instant.now());
        }
    }

    private void seedContributorTrust(String actorId, double globalScore,
                                       int observations, double mergeRate, double firstAttemptQuality) {
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
                ActorType.HUMAN, globalScore, observations, 0,
                globalScore * 10, (1 - globalScore) * 10,
                (int) (globalScore * observations), (int) ((1 - globalScore) * observations),
                Instant.now());

        trustRepo.upsert(actorId, ScoreType.CAPABILITY, "pr-contribution", null,
                ActorType.HUMAN, globalScore, observations, 0,
                globalScore * 10, (1 - globalScore) * 10,
                (int) (globalScore * observations), (int) ((1 - globalScore) * observations),
                Instant.now());

        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, "merge-rate",
                ActorType.HUMAN, mergeRate, observations, 0,
                mergeRate * 10, (1 - mergeRate) * 10,
                (int) (mergeRate * observations), (int) ((1 - mergeRate) * observations),
                Instant.now());

        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, "first-attempt-quality",
                ActorType.HUMAN, firstAttemptQuality, observations, 0,
                firstAttemptQuality * 10, (1 - firstAttemptQuality) * 10,
                (int) (firstAttemptQuality * observations), (int) ((1 - firstAttemptQuality) * observations),
                Instant.now());
    }

    private void seedPrReviewCases() {
        reviewService.startReview(new PrPayload("casehubio/engine", 142, "abc1234", "main", 320,
                "alice", 1001,
                List.of("src/auth/TokenService.java", "src/auth/SessionManager.java", "src/config/SecurityConfig.java")));

        reviewService.startReview(new PrPayload("casehubio/devtown", 218, "def5678", "main", 85,
                "bob", 1002,
                List.of("src/service/UserService.java", "src/util/Naming.java")));

        reviewService.startReview(new PrPayload("casehubio/ledger", 97, "fed9876", "main", 1200,
                "carol", 1003,
                List.of("src/payment/PaymentService.java", "src/payment/PaymentGateway.java",
                        "src/config/PaymentConfig.java", "src/order/OrderService.java")));

        reviewService.startReview(new PrPayload("casehubio/qhorus", 55, "cba4321", "main", 42,
                "dave", 1004,
                List.of("src/util/Helper.java")));
    }

    private void seedMergeQueue() {
        Instant now = Instant.now();
        mergeQueueService.enqueue(new QueuedPr(142, "casehubio/engine", "abc1234", "alice",
                0.88, PriorityLane.HIGH, now.minusSeconds(6300), Set.of()));
        mergeQueueService.enqueue(new QueuedPr(218, "casehubio/devtown", "def5678", "bob",
                0.72, PriorityLane.NORMAL, now.minusSeconds(4500), Set.of(142)));
        mergeQueueService.enqueue(new QueuedPr(97, "casehubio/ledger", "fed9876", "carol",
                0.45, PriorityLane.NORMAL, now.minusSeconds(11700), Set.of()));
        mergeQueueService.enqueue(new QueuedPr(55, "casehubio/qhorus", "cba4321", "dave",
                0.91, PriorityLane.CRITICAL, now.minusSeconds(1800), Set.of()));
        mergeQueueService.enqueue(new QueuedPr(301, "casehubio/platform", "111aaa2", "eve",
                0.63, PriorityLane.NORMAL, now.minusSeconds(7200), Set.of(97)));
    }
}
