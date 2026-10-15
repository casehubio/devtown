package io.casehub.devtown.app;

import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.casehub.devtown.domain.ContributorTrustCapability;
import io.casehub.devtown.domain.ContributorTrustDimension;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.domain.ReviewerTrustDimension;
import io.casehub.devtown.domain.queue.PriorityLane;
import io.casehub.devtown.queue.QueuedPr;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import io.casehub.devtown.review.sla.SlaCalibrationStore;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Startup
public class DevModeDataSeeder {

    private static final Logger LOG = Logger.getLogger(DevModeDataSeeder.class);

    @Inject PrReviewApplicationService reviewService;
    @Inject MergeQueueService mergeQueueService;
    @Inject PrReviewCaseTracker caseTracker;
    @Inject PrReviewCaseHub caseHub;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject SlaCalibrationStore slaStore;

    void onStart(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        if (LaunchMode.current() != LaunchMode.DEVELOPMENT) {
            return;
        }
        LOG.info("DevModeDataSeeder: seeding dashboard data for dev mode");
        try {
            seedTrustScores();
        } catch (Exception e) {
            LOG.warn("DevModeDataSeeder: trust seeding failed (non-fatal)", e);
        }
        try {
            seedPrReviews();
        } catch (Exception e) {
            LOG.warn("DevModeDataSeeder: PR review seeding failed (non-fatal)", e);
        }
        try {
            seedMergeQueue();
        } catch (Exception e) {
            LOG.warn("DevModeDataSeeder: merge queue seeding failed (non-fatal)", e);
        }
        try {
            seedSlaCalibration();
        } catch (Exception e) {
            LOG.warn("DevModeDataSeeder: SLA seeding failed (non-fatal)", e);
        }
        LOG.info("DevModeDataSeeder: seeding complete");
    }

    private void seedPrReviews() {
        submitPr(142, "casehubio/engine", "abc142abc142", 320, "alice", 2001,
            List.of("src/core/Engine.java", "src/core/Runtime.java", "src/auth/TokenHandler.java",
                "test/core/EngineTest.java", "pom.xml"));

        submitPr(218, "casehubio/devtown", "def218def218", 85, "bob", 2002,
            List.of("src/service/ReviewService.java", "src/util/Formatter.java",
                "test/service/ReviewServiceTest.java"));

        submitPr(97, "casehubio/ledger", "fed987fed987", 1200, "carol", 2003,
            List.of("src/ledger/MerkleTree.java", "src/ledger/AuditEntry.java",
                "src/auth/TokenHandler.java", "src/security/AccessControl.java",
                "src/config/SecurityConfig.java", "test/ledger/MerkleTreeTest.java",
                "test/ledger/AuditEntryTest.java", "docs/security.md",
                "db/migration/V200__ledger_tables.sql"));

        submitPr(55, "casehubio/qhorus", "aaa055aaa055", 42, "dave", 2004,
            List.of("src/qhorus/MessageBus.java", "test/qhorus/MessageBusTest.java"));

        LOG.info("DevModeDataSeeder: submitted 4 PR reviews");
    }

    private void submitPr(int prNumber, String repo, String sha, int lines, String contributor, long numericId, List<String> paths) {
        try {
            var pr = new PrPayload(repo, prNumber, sha, "main", lines, contributor, numericId, paths);
            var result = reviewService.startReview(pr);
            LOG.infof("  PR#%d %s by %s → %s", prNumber, repo, contributor, result.verdict());
        } catch (Exception e) {
            LOG.warnf("  PR#%d failed: %s", prNumber, e.getMessage());
        }
    }

    private void seedMergeQueue() {
        enqueuePr(142, "casehubio/engine", "abc142abc142", "alice", 0.88, PriorityLane.HIGH);
        enqueuePr(218, "casehubio/devtown", "def218def218", "bob", 0.72, PriorityLane.NORMAL);
        enqueuePr(97, "casehubio/ledger", "fed987fed987", "carol", 0.45, PriorityLane.NORMAL);
        enqueuePr(55, "casehubio/qhorus", "aaa055aaa055", "dave", 0.91, PriorityLane.HIGH);
        enqueuePr(301, "casehubio/platform", "eee301eee301", "eve", 0.63, PriorityLane.NORMAL);
        LOG.info("DevModeDataSeeder: enqueued 5 PRs to merge queue");
    }

    private void enqueuePr(int prNumber, String repo, String sha, String author, double trust, PriorityLane lane) {
        try {
            var pr = new QueuedPr(prNumber, repo, sha, author, trust, lane, Instant.now(), Set.of());
            mergeQueueService.enqueue(pr);
            LOG.infof("  Enqueued PR#%d %s (trust=%.2f lane=%s)", prNumber, repo, trust, lane);
        } catch (Exception e) {
            LOG.warnf("  Enqueue PR#%d failed: %s", prNumber, e.getMessage());
        }
    }

    private void seedSlaCalibration() {
        Instant now = Instant.now();
        String scope = "casehubio/devtown/pr-review";
        UUID refCase = UUID.randomUUID();
        slaStore.saveAll(List.of(
            new SlaCalibrationRecord(UUID.randomUUID(), "code-analysis", scope,
                Duration.ofMinutes(40), Duration.ofMinutes(15), Duration.ofMinutes(90), 47, refCase, now),
            new SlaCalibrationRecord(UUID.randomUUID(), "security-review", scope,
                Duration.ofMinutes(70), Duration.ofMinutes(30), Duration.ofMinutes(120), 32, refCase, now),
            new SlaCalibrationRecord(UUID.randomUUID(), "architecture-review", scope,
                Duration.ofMinutes(50), Duration.ofMinutes(20), Duration.ofMinutes(100), 12, refCase, now),
            new SlaCalibrationRecord(UUID.randomUUID(), "style-review", scope,
                Duration.ofMinutes(20), Duration.ofMinutes(5), Duration.ofMinutes(40), 55, refCase, now),
            new SlaCalibrationRecord(UUID.randomUUID(), "test-coverage", scope,
                Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofMinutes(60), 21, refCase, now)
        ));
        LOG.info("DevModeDataSeeder: seeded SLA calibration for 5 capabilities");
    }

    private void seedTrustScores() {
        Instant now = Instant.now();

        // Reviewer agents — capability scores
        seedAgent("agent-code-01", now, 47,
            new Cap(ReviewDomain.CODE_ANALYSIS, 0.85, 45, 2),
            new Cap(ReviewDomain.STYLE_REVIEW, 0.92, 50, 1));
        seedAgent("agent-sec-01", now, 32,
            new Cap(ReviewDomain.SECURITY_REVIEW, 0.78, 30, 5));
        seedAgent("agent-arch-01", now, 12,
            new Cap(ReviewDomain.ARCHITECTURE_REVIEW, 0.65, 10, 3));
        seedAgent("agent-perf-01", now, 21,
            new Cap(ReviewDomain.PERFORMANCE_ANALYSIS, 0.74, 19, 4));
        seedAgent("agent-test-01", now, 55,
            new Cap(ReviewDomain.TEST_COVERAGE, 0.88, 52, 3));
        seedAgent("agent-style-01", now, 3,
            new Cap(ReviewDomain.STYLE_REVIEW, 0.60, 3, 1));

        // Reviewer quality dimensions
        seedAgentDimensions("agent-code-01", now, 47, 0.88, 0.90, 0.82, 0.91);
        seedAgentDimensions("agent-sec-01", now, 32, 0.82, 0.75, 0.70, 0.85);
        seedAgentDimensions("agent-arch-01", now, 12, 0.70, 0.65, 0.60, 0.72);
        seedAgentDimensions("agent-perf-01", now, 21, 0.78, 0.80, 0.72, 0.76);
        seedAgentDimensions("agent-test-01", now, 55, 0.90, 0.88, 0.85, 0.92);
        seedAgentDimensions("agent-style-01", now, 3, 0.55, 0.50, 0.45, 0.60);

        // Contributors — global + capability + dimensions
        seedContributor("alice", now, 0.88, 34, 2, 0.94, 0.82);
        seedContributor("bob", now, 0.72, 18, 4, 0.78, 0.65);
        seedContributor("carol", now, 0.45, 8, 4, 0.50, 0.38);
        seedContributor("dave", now, 0.91, 52, 2, 0.96, 0.88);
        seedContributor("eve", now, 0.63, 11, 3, 0.73, 0.60);

        LOG.info("DevModeDataSeeder: seeded trust scores for 6 agents and 5 contributors");
    }

    private record Cap(String capability, double score, int positive, int negative) {}

    private void seedAgent(String actorId, Instant now, int totalDecisions, Cap... capabilities) {
        double globalScore = 0;
        for (Cap c : capabilities) {
            trustRepo.upsert(actorId, ScoreType.CAPABILITY, c.capability, null,
                ActorType.AGENT, c.score, totalDecisions, c.negative,
                1.0, 1.0, c.positive, c.negative, now);
            globalScore = Math.max(globalScore, c.score);
        }
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
            ActorType.AGENT, globalScore, totalDecisions, 0,
            1.0, 1.0, totalDecisions, 0, now);
    }

    private void seedAgentDimensions(String actorId, Instant now, int decisions,
                                     double thoroughness, double precision, double calibration, double responsiveness) {
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ReviewerTrustDimension.REVIEW_THOROUGHNESS,
            ActorType.AGENT, thoroughness, decisions, 0, 1.0, 1.0, decisions, 0, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ReviewerTrustDimension.PRECISION,
            ActorType.AGENT, precision, decisions, 0, 1.0, 1.0, decisions, 0, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ReviewerTrustDimension.SCOPE_CALIBRATION,
            ActorType.AGENT, calibration, decisions, 0, 1.0, 1.0, decisions, 0, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ReviewerTrustDimension.RESPONSIVENESS,
            ActorType.AGENT, responsiveness, decisions, 0, 1.0, 1.0, decisions, 0, now);
    }

    private void seedContributor(String actorId, Instant now, double globalScore, int observations,
                                 int overturned, double mergeRate, double firstAttemptQuality) {
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
            ActorType.HUMAN, globalScore, observations, overturned,
            1.0, 1.0, observations - overturned, overturned, now);
        trustRepo.upsert(actorId, ScoreType.CAPABILITY, ContributorTrustCapability.PR_CONTRIBUTION, null,
            ActorType.HUMAN, globalScore, observations, overturned,
            1.0, 1.0, observations - overturned, overturned, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ContributorTrustDimension.MERGE_RATE,
            ActorType.HUMAN, mergeRate, observations, 0, 1.0, 1.0, observations, 0, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, ContributorTrustDimension.FIRST_ATTEMPT_QUALITY,
            ActorType.HUMAN, firstAttemptQuality, observations, 0, 1.0, 1.0, observations, 0, now);
    }
}
