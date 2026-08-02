package io.casehub.devtown.review.sla;

import java.util.Optional;

public interface SlaCalibrationStore {

    void save(SlaCalibrationRecord record);

    Optional<SlaCalibrationRecord> findLatest(String capability, String scopePath);

    void saveAll(java.util.List<SlaCalibrationRecord> records);

    java.util.List<SlaCalibrationRecord> findLatestCalibration(String scopePath);

}
