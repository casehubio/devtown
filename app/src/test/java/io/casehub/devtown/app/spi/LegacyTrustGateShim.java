package io.casehub.devtown.app.spi;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Produces a bean typed as the old {@code io.casehub.ledger.runtime.service.TrustGateService}
 * for the casehub-qhorus SNAPSHOT that still references the pre-relocation package.
 * {@code @DefaultBean} yields to LedgerCoreProducer for the new type, avoiding ambiguity.
 * Remove once qhorus is rebuilt against the current ledger.
 */
@ApplicationScoped
public class LegacyTrustGateShim {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.ledger.runtime.service.TrustGateService legacyTrustGateService(
            TrustScoreSource source) {
        return new io.casehub.ledger.runtime.service.TrustGateService(source);
    }
}
