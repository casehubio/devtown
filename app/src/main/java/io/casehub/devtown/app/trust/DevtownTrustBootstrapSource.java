package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.BootstrapScoreComputer;
import io.casehub.devtown.domain.trust.ContributorGitHubProfile;
import io.casehub.devtown.domain.trust.RepoConfidenceTier;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.CapabilityDimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.CapabilityScoreExport;
import io.casehub.ledger.runtime.service.federation.DimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.TrustBootstrapSource;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DevtownTrustBootstrapSource implements TrustBootstrapSource {

    @Inject EntityManager em;

    @Override
    public Optional<TrustExportPayload> fetchPriorTrust(String actorId) {
        return findProfile(actorId).map(profile -> {
            var tier = findTier(profile.repo());
            var result = BootstrapScoreComputer.compute(profile, tier);
            if (result.isEmpty()) return null;

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
            return new TrustExportPayload(now, "devtown-github-bootstrap", List.of(actor));
        });
    }

    private Optional<ContributorGitHubProfile> findProfile(String actorId) {
        try {
            var entity = em.createQuery(
                "SELECT p FROM ContributorGitHubProfileEntity p WHERE p.actorId = :actorId",
                ContributorGitHubProfileEntity.class)
                .setParameter("actorId", actorId)
                .setMaxResults(1)
                .getSingleResult();
            return Optional.of(entity.toDomain());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private RepoConfidenceTier findTier(String repo) {
        try {
            var entity = em.createQuery(
                "SELECT r FROM RepoConfidenceProfileEntity r WHERE r.repo = :repo",
                RepoConfidenceProfileEntity.class)
                .setParameter("repo", repo)
                .setMaxResults(1)
                .getSingleResult();
            return entity.tier;
        } catch (NoResultException e) {
            return RepoConfidenceTier.LOW;
        }
    }
}
