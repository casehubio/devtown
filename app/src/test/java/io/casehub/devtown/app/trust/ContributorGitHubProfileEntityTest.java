package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.CacheMaturity;
import io.casehub.devtown.domain.trust.ContributorGitHubProfile;
import io.casehub.devtown.domain.trust.RepoConfidenceTier;
import io.casehub.devtown.domain.trust.RepoMetadataSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "test", roles = "admin")
class ContributorGitHubProfileEntityTest {

    @Inject EntityManager em;

    @Test
    @Transactional
    void persistAndRetrieve_contributorProfile() {
        var profile = new ContributorGitHubProfile("alice", 123L, "org/repo",
            "github-id:123", 10, 3, 13, Instant.now(), CacheMaturity.WARM);
        var entity = ContributorGitHubProfileEntity.fromDomain(profile);
        em.persist(entity);
        em.flush();
        em.clear();

        var found = em.find(ContributorGitHubProfileEntity.class, entity.id);
        assertNotNull(found);
        var domain = found.toDomain();
        assertEquals("alice", domain.login());
        assertEquals(123L, domain.contributorNumericId());
        assertEquals("github-id:123", domain.actorId());
        assertEquals(10, domain.mergedCount());
        assertEquals(3, domain.closedCount());
        assertEquals(13, domain.observationCount());
        assertEquals(CacheMaturity.WARM, domain.maturity());
    }

    @Test
    @Transactional
    void persistAndRetrieve_repoConfidenceProfile() {
        var snapshot = new RepoMetadataSnapshot("org/repo", 500, 20, 200,
            Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        var entity = RepoConfidenceProfileEntity.fromSnapshot(snapshot, RepoConfidenceTier.MEDIUM);
        em.persist(entity);
        em.flush();
        em.clear();

        var found = em.find(RepoConfidenceProfileEntity.class, entity.id);
        assertNotNull(found);
        var domain = found.toDomain();
        assertEquals("org/repo", domain.repo());
        assertEquals(500, domain.starCount());
        assertEquals(20, domain.contributorCount());
        assertEquals(RepoConfidenceTier.MEDIUM, domain.tier());
        assertFalse(domain.adminOverride());
    }
}
