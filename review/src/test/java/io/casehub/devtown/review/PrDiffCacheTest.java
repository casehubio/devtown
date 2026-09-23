package io.casehub.devtown.review;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PrDiffCacheTest {

    @Test
    void cachesResultByShaKey() {
        var callCount = new AtomicInteger(0);
        PrDiffService service = (repo, pr) -> {
            callCount.incrementAndGet();
            return new PrDiff(repo, pr, "base", "head", List.of(), false);
        };

        var cache = new PrDiffCache(service);

        var first = cache.get("org/repo", 1, "sha1");
        var second = cache.get("org/repo", 1, "sha1");

        assertSame(first, second);
        assertEquals(1, callCount.get());
    }

    @Test
    void differentShaBypassesCache() {
        var callCount = new AtomicInteger(0);
        PrDiffService service = (repo, pr) -> {
            callCount.incrementAndGet();
            return new PrDiff(repo, pr, "base", "head-" + callCount.get(), List.of(), false);
        };

        var cache = new PrDiffCache(service);

        cache.get("org/repo", 1, "sha1");
        cache.get("org/repo", 1, "sha2");

        assertEquals(2, callCount.get());
    }
}
