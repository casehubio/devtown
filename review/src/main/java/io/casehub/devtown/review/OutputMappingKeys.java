package io.casehub.devtown.review;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputMappingKeys {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("\\{\\s*\"?([a-zA-Z_][a-zA-Z0-9_]*)\"?\\s*:");

    private OutputMappingKeys() {}

    public static String topLevelKey(String outputMapping) {
        if (outputMapping == null) return null;
        Matcher m = TOP_LEVEL_KEY.matcher(outputMapping);
        return m.find() ? m.group(1) : null;
    }

    public static String contextKeyForCapability(String capability) {
        return switch (capability) {
            case "security-review" -> "securityReview";
            case "architecture-review" -> "architectureReview";
            case "style-review" -> "styleCheck";
            case "test-coverage" -> "testCoverage";
            case "performance-analysis" -> "performanceAnalysis";
            case "human-decision:pr-approval" -> "humanApproval";
            default -> null;
        };
    }

}
