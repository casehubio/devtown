-- Merge queue entry table
CREATE TABLE merge_queue_entry (
    pr_number       INTEGER NOT NULL,
    repository      VARCHAR(255) NOT NULL,
    head_sha        VARCHAR(40) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    trust_score     DOUBLE PRECISION NOT NULL,
    lane            VARCHAR(20) NOT NULL,
    enqueued_at     TIMESTAMP NOT NULL,
    depends_on      TEXT,
    work_item_id    UUID,
    status          VARCHAR(20) NOT NULL,
    prioritized     BOOLEAN NOT NULL DEFAULT false,
    batch_id        VARCHAR(100),
    CONSTRAINT pk_merge_queue_entry PRIMARY KEY (pr_number, repository)
);

CREATE INDEX idx_merge_queue_entry_status ON merge_queue_entry(status);
CREATE INDEX idx_merge_queue_entry_batch_id ON merge_queue_entry(batch_id);

-- Active batch table
CREATE TABLE merge_queue_batch (
    batch_id        VARCHAR(100) NOT NULL,
    case_id         UUID NOT NULL,
    pr_numbers      TEXT NOT NULL,
    repository      VARCHAR(255) NOT NULL,
    dispatched_at   TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP,
    succeeded       BOOLEAN,
    CONSTRAINT pk_merge_queue_batch PRIMARY KEY (batch_id)
);

CREATE INDEX idx_merge_queue_batch_case_id ON merge_queue_batch(case_id);
CREATE INDEX idx_merge_queue_batch_repo_completed ON merge_queue_batch(repository, completed_at);

-- SLA calibration record table
CREATE TABLE sla_calibration_record (
    id              UUID         NOT NULL PRIMARY KEY,
    capability      VARCHAR(255) NOT NULL,
    scope_path      VARCHAR(512) NOT NULL,
    median_seconds  BIGINT       NOT NULL,
    min_seconds     BIGINT       NOT NULL,
    max_seconds     BIGINT       NOT NULL,
    precedent_count INTEGER      NOT NULL,
    case_id         UUID,
    computed_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sla_calibration_capability_scope ON sla_calibration_record(capability, scope_path, computed_at DESC);
CREATE INDEX idx_sla_calibration_scope_computed ON sla_calibration_record(scope_path, computed_at DESC);
