CREATE TABLE merge_decision_ledger_entry (
    id          UUID NOT NULL,
    pr_number   INTEGER NOT NULL,
    repository  VARCHAR(255) NOT NULL,
    commit_sha  VARCHAR(40),
    decision    VARCHAR(20) NOT NULL,
    case_id     UUID NOT NULL,
    tenancy_id  VARCHAR(64) NOT NULL,
    CONSTRAINT pk_merge_decision_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_merge_decision_ledger_entry_id
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_merge_decision_entry_case_id ON merge_decision_ledger_entry(case_id);
CREATE INDEX idx_merge_decision_entry_tenancy_id ON merge_decision_ledger_entry(tenancy_id);
