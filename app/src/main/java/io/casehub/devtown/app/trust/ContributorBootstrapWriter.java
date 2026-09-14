package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.BootstrapScoreComputer;
import io.casehub.devtown.domain.trust.CacheMaturity;
import io.casehub.devtown.domain.trust.ContributorGitHubProfile;
import io.casehub.devtown.domain.trust.ContributorHistoryClient;
import io.casehub.devtown.domain.trust.RepoConfidenceTier;
import io.casehub.devtown.review.BootstrapContributorEvent;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.CapabilityDimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.CapabilityScoreExport;
import io.casehub.ledger.runtime.service.federation.DimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.casehub.ledger.runtime.service.federation.TrustImportService;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ContributorBootstrapWriter {

    private static final Logger LOG = Logger.getLogger(ContributorBootstrapWriter.class);
    private final ConcurrentHashMap<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    private final ContributorHistoryClient historyClient;
    private final TrustImportService importService;

    @Inject EntityManager em;

    @Inject
    public ContributorBootstrapWriter(ContributorHistoryClient historyClient,
                                       TrustImportService importService) {
        this.historyClient = historyClient;
        this.importService = importService;
    }

    public void onBootstrap(@ObservesAsync BootstrapContributorEvent event) {
        String key = event.login() + ":" + event.repo();
        var future = new CompletableFuture<Void>();
        var existing = inFlight.putIfAbsent(key, future);
        if (existing != null) {
            LOG.debugf("Bootstrap already in-flight for %s — skipping", key);
            return;
        }
        try {
            doBootstrap(event);
        } catch (Exception e) {
            LOG.warnf(e, "Bootstrap failed for %s — contributor remains in TRIAGE", key);
        } finally {
            inFlight.remove(key);
            future.complete(null);
        }
    }

    void doBootstrap(BootstrapContributorEvent event) {
        var snapshot = historyClient.fetchHistory(event.login(), event.repo(), Instant.MIN);
        String[] repoParts = event.repo().split("/");
        var meta = historyClient.fetchRepoMetadata(repoParts[0], repoParts[1]);

        var tier = RepoConfidenceTier.classify(meta.contributorCount(),
            meta.pullRequestCount(), meta.createdAt(), Instant.now());

        String actorId = ContributorGitHubProfile.actorIdFromGitHubNumericId(
            event.contributorNumericId());
        int observationCount = snapshot.mergedCount() + snapshot.closedCount();
        var profile = new ContributorGitHubProfile(
            event.login(), event.contributorNumericId(), event.repo(),
            actorId, snapshot.mergedCount(), snapshot.closedCount(),
            observationCount, Instant.now(),
            CacheMaturity.forObservationCount(observationCount));

        var result = BootstrapScoreComputer.compute(profile, tier);
        if (!result.isEmpty()) {
            importTrust(actorId, result);
        }

        persistProfile(profile);
        persistRepoMetadata(meta, tier);
        LOG.infof("Bootstrapped contributor %s in %s: %d merged, %d closed, tier=%s",
            event.login(), event.repo(), snapshot.mergedCount(), snapshot.closedCount(), tier);
    }

    private void importTrust(String actorId, BootstrapScoreComputer.BootstrapScoreResult result) {
        var now = Instant.now();
        var capScore = new CapabilityScoreExport(
            result.capabilityTag(), result.alpha(), result.beta(),
            result.trustScore(), result.observationCount(),
            (int) Math.round(result.alpha()), (int) Math.round(result.beta()), now);

        var dimScore = new DimensionScoreExport(
            result.mergeRateDimension(), result.mergeRate(),
            result.observationCount(), now);

        var capDimScore = new CapabilityDimensionScoreExport(
            result.capabilityTag(), result.mergeRateDimension(),
            result.mergeRate(), result.observationCount(), now);

        var actor = new ActorExport(actorId, ActorType.HUMAN, null,
            List.of(capScore), List.of(dimScore), List.of(capDimScore));

        var payload = new TrustExportPayload(now, "devtown-github-bootstrap", List.of(actor));
        importService.importTrust(payload);
    }

    @Transactional
    void persistProfile(ContributorGitHubProfile profile) {
        if (em == null) {return;}
        var existing = em.createQuery(
                                 "SELECT p FROM ContributorGitHubProfileEntity p WHERE p.login = :login AND p.repo = :repo",
                                 ContributorGitHubProfileEntity.class)
                         .setParameter("login", profile.login())
                         .setParameter("repo", profile.repo())
                         .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            existing.mergedCount      = profile.mergedCount();
            existing.closedCount      = profile.closedCount();
            existing.observationCount = profile.observationCount();
            existing.lastRefreshAt    = profile.lastRefreshAt();
            existing.maturity         = profile.maturity();
        } else {
            em.persist(ContributorGitHubProfileEntity.fromDomain(profile));
        }
    }

    @Transactional
    void persistRepoMetadata(io.casehub.devtown.domain.trust.RepoMetadataSnapshot meta,
                              RepoConfidenceTier tier) {
        if (em == null) {return;}
        var existing = em.createQuery(
                                 "SELECT r FROM RepoConfidenceProfileEntity r WHERE r.repo = :repo",
                                 RepoConfidenceProfileEntity.class)
                         .setParameter("repo", meta.repo())
                         .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            existing.starCount        = meta.starCount();
            existing.contributorCount = meta.contributorCount();
            existing.pullRequestCount = meta.pullRequestCount();
            existing.lastPushedAt     = meta.lastPushedAt();
            if (!existing.adminOverride) {existing.tier = tier;}
            existing.lastRefreshAt = java.time.Instant.now();
        } else {
            em.persist(RepoConfidenceProfileEntity.fromSnapshot(meta, tier));
        }
    }
}
