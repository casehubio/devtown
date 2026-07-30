package io.casehub.devtown.review.sla;

import java.util.Optional;

public interface SlaCalibrationStore {

    void save(SlaCalibrationRecord record);

    Optional<SlaCalibrationRecord> findLatest(String capability, String scopePath);
}
