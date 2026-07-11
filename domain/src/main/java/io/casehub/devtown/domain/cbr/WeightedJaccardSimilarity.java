package io.casehub.devtown.domain.cbr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WeightedJaccardSimilarity implements SimilarityMetric {

    private final double weightFilePaths;
    private final double weightModules;
    private final double weightLanguages;
    private final double weightChangeSize;
    private final double weightContributor;

    public WeightedJaccardSimilarity(double weightFilePaths, double weightModules,
                                      double weightLanguages, double weightChangeSize,
                                      double weightContributor) {
        this.weightFilePaths = weightFilePaths;
        this.weightModules = weightModules;
        this.weightLanguages = weightLanguages;
        this.weightChangeSize = weightChangeSize;
        this.weightContributor = weightContributor;
    }

    @Override
    public SimilarityScore compute(PrFeatureVector a, PrFeatureVector b) {
        var breakdown = new LinkedHashMap<String, Double>();

        double pathScore = jaccard(a.changedPaths(), b.changedPaths());
        breakdown.put("file-paths", pathScore);

        double moduleScore = jaccard(a.modules(), b.modules());
        breakdown.put("modules", moduleScore);

        double langScore = jaccard(a.languages(), b.languages());
        breakdown.put("languages", langScore);

        double sizeScore = changeSizeRatio(a.linesChanged(), b.linesChanged());
        breakdown.put("change-size", sizeScore);

        double contribScore = a.contributor().equals(b.contributor()) ? 1.0 : 0.0;
        breakdown.put("contributor", contribScore);

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        weightedSum += accumulate(weightFilePaths, pathScore);
        totalWeight += activeWeight(weightFilePaths);

        weightedSum += accumulate(weightModules, moduleScore);
        totalWeight += activeWeight(weightModules);

        weightedSum += accumulate(weightLanguages, langScore);
        totalWeight += activeWeight(weightLanguages);

        weightedSum += accumulate(weightChangeSize, sizeScore);
        totalWeight += activeWeight(weightChangeSize);

        weightedSum += accumulate(weightContributor, contribScore);
        totalWeight += activeWeight(weightContributor);

        double score = totalWeight > 0.0 ? weightedSum / totalWeight : 0.0;
        return new SimilarityScore(score, breakdown);
    }

    private static double accumulate(double weight, double dimensionScore) {
        return weight > 0.0 ? weight * dimensionScore : 0.0;
    }

    private static double activeWeight(double weight) {
        return weight > 0.0 ? weight : 0.0;
    }

    static double jaccard(Set<?> a, Set<?> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        long intersectionSize = a.stream().filter(b::contains).count();
        long unionSize = a.size() + b.size() - intersectionSize;
        return unionSize == 0 ? 1.0 : (double) intersectionSize / unionSize;
    }

    static double changeSizeRatio(int a, int b) {
        if (a == 0 && b == 0) return 1.0;
        return 1.0 - (double) Math.abs(a - b) / Math.max(a, b);
    }
}
