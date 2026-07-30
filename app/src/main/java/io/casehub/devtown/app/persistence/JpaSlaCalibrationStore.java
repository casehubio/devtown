package io.casehub.devtown.app.persistence;

import io.casehub.devtown.review.sla.SlaCalibrationRecord;
import io.casehub.devtown.review.sla.SlaCalibrationStore;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
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
}
