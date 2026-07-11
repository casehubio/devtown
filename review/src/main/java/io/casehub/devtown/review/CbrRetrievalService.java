package io.casehub.devtown.review;

import io.casehub.devtown.domain.cbr.PrFeatureVector;

import java.util.List;

public interface CbrRetrievalService {
    List<Precedent> findSimilar(PrFeatureVector query, String repo, String tenantId);
}
