package io.casehub.devtown.app.persistence;

import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import io.casehub.devtown.review.sla.SlaCalibrationStore;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class JpaSlaCalibrationStore implements SlaCalibrationStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    @Transactional
    public void save(SlaCalibrationRecord record) {
        em.persist(SlaCalibrationEntity.from(record));
    }

    @Override
    @jakarta.transaction.Transactional
    public void saveAll(java.util.List<io.casehub.devtown.review.sla.SlaCalibrationRecord> records) {
        records.forEach(r -> em.persist(SlaCalibrationEntity.from(r)));
    }


    @Override
    public Optional<SlaCalibrationRecord> findLatest(String capability, String scopePath) {
        return em.createQuery(
                "SELECT e FROM SlaCalibrationEntity e WHERE e.capability = :cap AND e.scopePath = :scope ORDER BY e.computedAt DESC",
                SlaCalibrationEntity.class)
            .setParameter("cap", capability)
            .setParameter("scope", scopePath)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .map(SlaCalibrationEntity::toRecord);
    }

    @Override
    public java.util.List<io.casehub.devtown.review.sla.SlaCalibrationRecord> findLatestCalibration(String scopePath) {
        return em.createQuery(
                         "SELECT e FROM SlaCalibrationEntity e WHERE e.scopePath = :scope " +
                         "AND e.computedAt = (SELECT MAX(e2.computedAt) FROM SlaCalibrationEntity e2 " +
                         "WHERE e2.scopePath = :scope)", SlaCalibrationEntity.class)
                 .setParameter("scope", scopePath)
                 .getResultStream()
                 .map(SlaCalibrationEntity::toRecord)
                 .toList();
    }

}
