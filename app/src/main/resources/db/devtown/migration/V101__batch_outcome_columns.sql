ALTER TABLE merge_queue_batch ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE merge_queue_batch ADD COLUMN succeeded BOOLEAN;
CREATE INDEX idx_merge_queue_batch_repo_completed
    ON merge_queue_batch(repository, completed_at);
