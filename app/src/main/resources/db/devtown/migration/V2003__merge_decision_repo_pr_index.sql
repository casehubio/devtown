CREATE INDEX idx_merge_decision_repo_pr ON merge_decision_ledger_entry(repository, pr_number);

DROP INDEX IF EXISTS idx_merge_decision_entry_tenancy_id;
ALTER TABLE merge_decision_ledger_entry DROP COLUMN IF EXISTS tenancy_id;
