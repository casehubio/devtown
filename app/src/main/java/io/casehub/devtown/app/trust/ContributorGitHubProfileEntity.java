package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.CacheMaturity;
import io.casehub.devtown.domain.trust.ContributorGitHubProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contributor_github_profile")
public class ContributorGitHubProfileEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String login;

    @Column(name = "contributor_numeric_id", nullable = false)
    public long contributorNumericId;

    @Column(nullable = false)
    public String repo;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "merged_count")
    public int mergedCount;

    @Column(name = "closed_count")
    public int closedCount;

    @Column(name = "observation_count")
    public int observationCount;

    @Column(name = "last_refresh_at")
    public Instant lastRefreshAt;

    @Enumerated(EnumType.STRING)
    public CacheMaturity maturity;

    public ContributorGitHubProfile toDomain() {
        return new ContributorGitHubProfile(login, contributorNumericId, repo, actorId,
            mergedCount, closedCount, observationCount, lastRefreshAt, maturity);
    }

    public static ContributorGitHubProfileEntity fromDomain(ContributorGitHubProfile p) {
        var e = new ContributorGitHubProfileEntity();
        e.id = UUID.randomUUID();
        e.login = p.login();
        e.contributorNumericId = p.contributorNumericId();
        e.repo = p.repo();
        e.actorId = p.actorId();
        e.mergedCount = p.mergedCount();
        e.closedCount = p.closedCount();
        e.observationCount = p.observationCount();
        e.lastRefreshAt = p.lastRefreshAt();
        e.maturity = p.maturity();
        return e;
    }
}
