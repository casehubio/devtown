package io.casehub.devtown.review;

import io.casehub.devtown.domain.CoordinatedChangeRequest;

public interface CoordinatedChangePort {
    CoordinatedChangeOutcome start(CoordinatedChangeRequest request);
}
