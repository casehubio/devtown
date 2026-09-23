package io.casehub.devtown.review;

import java.util.List;
import java.util.Map;

public record PrPayload(
        String repo,
        int prNumber,
        String headSha,
        String baseRef,
        int linesChanged,
        String contributor,
        long contributorNumericId,
        List<String> changedPaths
) {
    @SuppressWarnings("unchecked")
    public static PrPayload fromContextMap(Map<String, Object> prMap) {
        return new PrPayload(
                (String) prMap.get("repo"),
                Integer.parseInt(String.valueOf(prMap.get("id"))),
                (String) prMap.get("headSha"),
                (String) prMap.get("baseRef"),
                ((Number) prMap.get("linesChanged")).intValue(),
                (String) prMap.get("contributor"),
                -1L,
                (List<String>) prMap.get("changedPaths"));
    }
}
