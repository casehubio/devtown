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

CREATE INDEX idx_sla_calibration_capability_scope
    ON sla_calibration_record (capability, scope_path, computed_at DESC);
