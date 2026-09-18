package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.TrustScoreSource;

/**
 * Binary-compatibility shim — the casehub-qhorus SNAPSHOT references TrustGateService
 * at this old package location, but the current ledger relocated it to
 * {@code io.casehub.ledger.core.trust.TrustGateService}. This class only exists so
 * the classloader can resolve RoutingBridge and DefaultObligorTrustPolicy without a
 * ClassNotFoundException. Remove once qhorus is rebuilt against the current ledger.
 */
public class TrustGateService extends io.casehub.ledger.core.trust.TrustGateService {
    public TrustGateService(TrustScoreSource source) {
        super(source);
    }
}
