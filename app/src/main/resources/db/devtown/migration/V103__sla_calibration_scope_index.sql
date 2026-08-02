CREATE INDEX idx_sla_calibration_scope_computed
    ON sla_calibration_record (scope_path, computed_at DESC);
