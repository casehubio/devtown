-- V3000: test-only — message_ledger_entry subclass table from qhorus V2000
-- Duplicated here to avoid V2000 collision when db/qhorus/migration and
-- db/engine-ledger/migration are combined into one Flyway instance.
-- The LedgerEntry JOINED inheritance query LEFT JOINs to this table even
-- when no MessageLedgerEntry rows exist.

CREATE TABLE IF NOT EXISTS message_ledger_entry (
    id            UUID         NOT NULL,
    channel_id    UUID         NOT NULL,
    message_id    BIGINT       NOT NULL,
    message_type  VARCHAR(50)  NOT NULL,
    target        VARCHAR(255),
    content       TEXT,
    correlation_id VARCHAR(255),
    commitment_id UUID,
    tool_name     VARCHAR(255),
    duration_ms   BIGINT,
    token_count   BIGINT,
    context_refs  TEXT,
    source_entity TEXT,
    CONSTRAINT pk_message_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_message_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
