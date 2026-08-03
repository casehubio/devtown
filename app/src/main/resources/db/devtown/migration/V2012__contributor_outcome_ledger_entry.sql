CREATE TABLE contributor_outcome_ledger_entry (
    id              UUID NOT NULL,
    pr_number       INTEGER NOT NULL,
    repository      VARCHAR(255) NOT NULL,
    outcome         VARCHAR(30) NOT NULL,
    review_rounds   INTEGER,
    case_id         UUID,
    CONSTRAINT pk_contributor_outcome_entry PRIMARY KEY (id)
);

CREATE INDEX idx_contributor_outcome_repo_pr
    ON contributor_outcome_ledger_entry(repository, pr_number);
CREATE INDEX idx_contributor_outcome_case_id
    ON contributor_outcome_ledger_entry(case_id);
