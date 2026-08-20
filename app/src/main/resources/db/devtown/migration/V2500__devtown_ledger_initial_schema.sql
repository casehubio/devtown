-- Merge decision ledger entry (ledger subclass join table)
CREATE TABLE merge_decision_ledger_entry (
    id                  UUID NOT NULL,
    pr_number           INTEGER NOT NULL,
    repository          VARCHAR(255) NOT NULL,
    commit_sha          VARCHAR(40),
    decision            VARCHAR(20) NOT NULL,
    case_id             UUID NOT NULL,
    tenancy_id          VARCHAR(64) NOT NULL,
    batch_id            VARCHAR(64),
    batch_size          INTEGER,
    bisection_occurred  BOOLEAN,
    bisection_strategy  VARCHAR(30),
    batch_context_json  TEXT,
    CONSTRAINT pk_merge_decision_ledger_entry PRIMARY KEY (id)
);

CREATE INDEX idx_merge_decision_entry_case_id ON merge_decision_ledger_entry(case_id);
CREATE INDEX idx_merge_decision_entry_tenancy_id ON merge_decision_ledger_entry(tenancy_id);
CREATE INDEX idx_merge_decision_entry_batch_id ON merge_decision_ledger_entry(batch_id);
CREATE INDEX idx_merge_decision_repo_pr ON merge_decision_ledger_entry(repository, pr_number);

-- Contributor outcome ledger entry (ledger subclass join table)
CREATE TABLE contributor_outcome_ledger_entry (
    id              UUID NOT NULL,
    pr_number       INTEGER NOT NULL,
    repository      VARCHAR(255) NOT NULL,
    outcome         VARCHAR(30) NOT NULL,
    review_rounds   INTEGER,
    case_id         UUID,
    CONSTRAINT pk_contributor_outcome_entry PRIMARY KEY (id)
);

CREATE INDEX idx_contributor_outcome_repo_pr ON contributor_outcome_ledger_entry(repository, pr_number);
CREATE INDEX idx_contributor_outcome_case_id ON contributor_outcome_ledger_entry(case_id);
