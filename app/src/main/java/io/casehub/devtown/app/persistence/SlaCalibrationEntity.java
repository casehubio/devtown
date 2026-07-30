package io.casehub.devtown.app.persistence;

import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sla_calibration_record")
public class SlaCalibrationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String capability;

    @Column(name = "scope_path", nullable = false)
    private String scopePath;

    @Column(name = "median_seconds", nullable = false)
    private long medianSeconds;

    @Column(name = "min_seconds", nullable = false)
    private long minSeconds;

    @Column(name = "max_seconds", nullable = false)
    private long maxSeconds;

    @Column(name = "precedent_count", nullable = false)
    private int precedentCount;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected SlaCalibrationEntity() {}

    public static SlaCalibrationEntity from(SlaCalibrationRecord record) {
        var e = new SlaCalibrationEntity();
        e.id = record.id();
        e.capability = record.capability();
        e.scopePath = record.scopePath();
        e.medianSeconds = record.median().toSeconds();
        e.minSeconds = record.min().toSeconds();
        e.maxSeconds = record.max().toSeconds();
        e.precedentCount = record.precedentCount();
        e.caseId = record.caseId();
        e.computedAt = record.computedAt();
        return e;
    }

    public SlaCalibrationRecord toRecord() {
        return new SlaCalibrationRecord(
            id, capability, scopePath,
            Duration.ofSeconds(medianSeconds),
            Duration.ofSeconds(minSeconds),
            Duration.ofSeconds(maxSeconds),
            precedentCount, caseId, computedAt);
    }
}
