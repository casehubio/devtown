CREATE TABLE erasure_receipt_ledger_entry (
    id                       UUID NOT NULL,
    erased_actor_token       VARCHAR(255) NOT NULL,
    reason                   VARCHAR(1000),
    ledger_entries_affected  BIGINT NOT NULL,
    memory_records_erased    INTEGER NOT NULL,
    CONSTRAINT pk_erasure_receipt_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_erasure_receipt_ledger_entry_id
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_erasure_receipt_actor_token ON erasure_receipt_ledger_entry(erased_actor_token);
