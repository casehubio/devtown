package io.casehub.devtown.review;

public interface PrDiffService {
    PrDiff fetchDiff(String repo, int prNumber);
}
