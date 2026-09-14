package io.casehub.devtown.app.trust;

import io.casehub.devtown.domain.trust.RepoConfidenceProfile;
import io.casehub.devtown.domain.trust.RepoConfidenceTier;
import io.casehub.devtown.domain.trust.RepoMetadataSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repo_confidence_profile")
public class RepoConfidenceProfileEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String repo;

    @Column(name = "star_count")
    public int starCount;

    @Column(name = "contributor_count")
    public int contributorCount;

    @Column(name = "pull_request_count")
    public int pullRequestCount;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "last_pushed_at")
    public Instant lastPushedAt;

    @Enumerated(EnumType.STRING)
    public RepoConfidenceTier tier;

    @Column(name = "admin_override")
    public boolean adminOverride;

    @Column(name = "last_refresh_at")
    public Instant lastRefreshAt;

    public RepoConfidenceProfile toDomain() {
        return new RepoConfidenceProfile(repo, starCount, contributorCount,
            pullRequestCount, createdAt, lastPushedAt, tier, adminOverride, lastRefreshAt);
    }

    public static RepoConfidenceProfileEntity fromSnapshot(RepoMetadataSnapshot meta,
                                                            RepoConfidenceTier tier) {
        var e = new RepoConfidenceProfileEntity();
        e.id = UUID.randomUUID();
        e.repo = meta.repo();
        e.starCount = meta.starCount();
        e.contributorCount = meta.contributorCount();
        e.pullRequestCount = meta.pullRequestCount();
        e.createdAt = meta.createdAt();
        e.lastPushedAt = meta.lastPushedAt();
        e.tier = tier;
        e.adminOverride = false;
        e.lastRefreshAt = Instant.now();
        return e;
    }
}
