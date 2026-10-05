package io.casehub.devtown.app.spi;

import io.casehub.devtown.app.mcp.CaseInfo;
import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.PrDiffService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class DevModePrDiffService implements PrDiffService {

    private static final Logger LOG = Logger.getLogger(DevModePrDiffService.class);

    @Inject PrReviewCaseTracker caseTracker;

    @Override
    public PrDiff fetchDiff(String repo, int prNumber) {
        LOG.infof("Dev-mode: generating synthetic diff for %s#%d", repo, prNumber);

        Set<String> changedPaths = resolveChangedPaths(repo, prNumber);

        List<PrDiff.FileDiff> files = new ArrayList<>();
        for (String path : changedPaths) {
            String patch = SyntheticPatches.PATCHES.get(path);
            if (patch != null) {
                files.add(new PrDiff.FileDiff(
                    path,
                    patch.contains("@@ -0,0") ? "added" : "modified",
                    patch,
                    countLines(patch, '+'),
                    countLines(patch, '-')
                ));
            } else {
                files.add(new PrDiff.FileDiff(path, "modified",
                    "@@ -1,3 +1,5 @@\n+// modified\n", 1, 0));
            }
        }

        if (files.isEmpty()) {
            LOG.warnf("Dev-mode: no synthetic patches for %s#%d — returning minimal diff", repo, prNumber);
            files.add(new PrDiff.FileDiff("README.md", "modified",
                "@@ -1,1 +1,2 @@\n+// updated\n", 1, 0));
        }

        return new PrDiff(repo, prNumber, "dev-base", "dev-" + prNumber, files, false);
    }

    private Set<String> resolveChangedPaths(String repo, int prNumber) {
        var activeCase = caseTracker.findActiveCaseByPr(repo, prNumber);
        if (activeCase.isPresent()) {
            CaseInfo info = activeCase.get();
            if (info.payload().changedPaths() != null && !info.payload().changedPaths().isEmpty()) {
                return Set.copyOf(info.payload().changedPaths());
            }
        }
        return SyntheticPatches.PATCHES.keySet();
    }

    private static int countLines(String patch, char prefix) {
        int count = 0;
        for (String line : patch.split("\n")) {
            if (!line.isEmpty() && line.charAt(0) == prefix) {
                count++;
            }
        }
        return count;
    }
}
