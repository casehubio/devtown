package io.casehub.devtown.github;

import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrDiffService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GitHubPrDiffClient implements PrDiffService {

    private static final int PAGE_SIZE = 100;
    private static final int GITHUB_FILE_CAP = 3000;

    private final GitHubPullRequestApi api;

    @Inject
    public GitHubPrDiffClient(@RestClient GitHubPullRequestApi api) {
        this.api = api;
    }

    @Override
    public PrDiff fetchDiff(String repo, int prNumber) {
        String[] parts = repo.split("/", 2);
        String owner = parts[0];
        String repoName = parts[1];

        List<PrDiff.FileDiff> allFiles = new ArrayList<>();
        int page = 1;

        while (true) {
            List<Map<String, Object>> pageFiles =
                api.listPullRequestFiles(owner, repoName, prNumber, PAGE_SIZE, page);

            for (var fileMap : pageFiles) {
                allFiles.add(toFileDiff(fileMap));
            }

            if (pageFiles.size() < PAGE_SIZE) {
                break;
            }
            page++;

            if (allFiles.size() >= GITHUB_FILE_CAP) {
                break;
            }
        }

        boolean truncated = allFiles.size() >= GITHUB_FILE_CAP;
        return new PrDiff(repo, prNumber, null, null, allFiles, truncated);
    }

    private static PrDiff.FileDiff toFileDiff(Map<String, Object> map) {
        String path = (String) map.get("filename");
        String status = (String) map.get("status");
        String patch = (String) map.get("patch");
        int additions = map.containsKey("additions") ? ((Number) map.get("additions")).intValue() : 0;
        int deletions = map.containsKey("deletions") ? ((Number) map.get("deletions")).intValue() : 0;
        return new PrDiff.FileDiff(path, status, patch, additions, deletions);
    }
}
