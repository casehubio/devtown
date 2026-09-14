-- GitHub-derived contributor intelligence cache (#202)

CREATE TABLE contributor_github_profile (
    id                      UUID         NOT NULL PRIMARY KEY,
    login                   VARCHAR(255) NOT NULL,
    contributor_numeric_id  BIGINT       NOT NULL,
    repo                    VARCHAR(255) NOT NULL,
    actor_id                VARCHAR(255) NOT NULL,
    merged_count            INTEGER      NOT NULL DEFAULT 0,
    closed_count            INTEGER      NOT NULL DEFAULT 0,
    observation_count       INTEGER      NOT NULL DEFAULT 0,
    last_refresh_at         TIMESTAMP,
    maturity                VARCHAR(20)  NOT NULL DEFAULT 'COLD',
    CONSTRAINT uq_contributor_github_profile UNIQUE (login, repo)
);

CREATE INDEX idx_cgp_actor_id ON contributor_github_profile(actor_id);

CREATE TABLE repo_confidence_profile (
    id                  UUID         NOT NULL PRIMARY KEY,
    repo                VARCHAR(255) NOT NULL,
    star_count          INTEGER      NOT NULL DEFAULT 0,
    contributor_count   INTEGER      NOT NULL DEFAULT 0,
    pull_request_count  INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP,
    last_pushed_at      TIMESTAMP,
    tier                VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    admin_override      BOOLEAN      NOT NULL DEFAULT false,
    last_refresh_at     TIMESTAMP,
    CONSTRAINT uq_repo_confidence_profile UNIQUE (repo)
);
