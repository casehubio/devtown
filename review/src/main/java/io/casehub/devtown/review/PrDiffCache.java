package io.casehub.devtown.review;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PrDiffCache {

    private final PrDiffService diffService;
    private final ConcurrentHashMap<CacheKey, PrDiff> cache = new ConcurrentHashMap<>();

    @Inject
    public PrDiffCache(PrDiffService diffService) {
        this.diffService = diffService;
    }

    public PrDiff get(String repo, int prNumber, String headSha) {
        return cache.computeIfAbsent(
            new CacheKey(repo, prNumber, headSha),
            k -> diffService.fetchDiff(k.repo(), k.prNumber()));
    }

    record CacheKey(String repo, int prNumber, String headSha) {}
}
