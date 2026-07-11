package io.casehub.devtown.domain.cbr;

import io.casehub.devtown.domain.memory.ModulePathNormalizer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record PrFeatureVector(
    String repo,
    int prNumber,
    String contributor,
    int linesChanged,
    Set<String> changedPaths,
    Set<String> modules,
    Set<String> languages,
    boolean hasTests,
    boolean touchedConfigs
) {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        Map.entry(".java", "java"),
        Map.entry(".kt", "kotlin"), Map.entry(".kts", "kotlin"),
        Map.entry(".ts", "typescript"), Map.entry(".tsx", "typescript"),
        Map.entry(".js", "javascript"), Map.entry(".jsx", "javascript"),
        Map.entry(".py", "python"),
        Map.entry(".go", "go"),
        Map.entry(".rs", "rust"),
        Map.entry(".rb", "ruby"),
        Map.entry(".scala", "scala"),
        Map.entry(".c", "c"), Map.entry(".cpp", "c"), Map.entry(".h", "c"),
        Map.entry(".cs", "csharp"),
        Map.entry(".swift", "swift"),
        Map.entry(".sh", "shell"),
        Map.entry(".xml", "config"), Map.entry(".yaml", "config"), Map.entry(".yml", "config"),
        Map.entry(".json", "config"), Map.entry(".properties", "config")
    );

    private static final Pattern TEST_PATH_PATTERN = Pattern.compile(
        "(?:^|/)(test|tests|__tests__)/|" +
        "Test\\.java$|" +
        "\\.test\\.tsx?$|\\.spec\\.tsx?$|" +
        "_test\\.go$|" +
        "_test\\.py$|test_[^/]*\\.py$"
    );

    private static final Set<String> CONFIG_FILENAMES = Set.of(
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "gradle.properties",
        "package.json", "tsconfig.json",
        "requirements.txt", "setup.py", "setup.cfg", "pyproject.toml",
        "Cargo.toml", "go.mod", "go.sum",
        "Dockerfile", "Makefile", "Jenkinsfile", "Rakefile",
        ".prettierrc"
    );

    private static final Pattern CONFIG_PATH_PATTERN = Pattern.compile(
        "\\.properties$|\\.yaml$|\\.yml$|\\.json$|\\.eslintrc\\.|^\\.github/"
    );

    public static PrFeatureVector from(String repo, int prNumber, String contributor,
                                        int linesChanged, List<String> changedPaths) {
        Set<String> pathSet = Set.copyOf(changedPaths);
        Set<String> mods = Set.copyOf(ModulePathNormalizer.normalize(changedPaths));
        Set<String> langs = extractLanguages(changedPaths);
        boolean tests = changedPaths.stream().anyMatch(p -> TEST_PATH_PATTERN.matcher(p).find());
        boolean configs = changedPaths.stream().anyMatch(PrFeatureVector::isConfig);
        return new PrFeatureVector(repo, prNumber, contributor, linesChanged,
            pathSet, mods, langs, tests, configs);
    }

    private static Set<String> extractLanguages(List<String> paths) {
        var result = new LinkedHashSet<String>();
        for (var path : paths) {
            String ext = extractExtension(path);
            if (ext != null) {
                String lang = EXTENSION_TO_LANGUAGE.get(ext);
                if (lang != null) {
                    result.add(lang);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String extractExtension(String path) {
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return null;
        return filename.substring(dot);
    }

    private static boolean isConfig(String path) {
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (CONFIG_FILENAMES.contains(filename)) return true;
        return CONFIG_PATH_PATTERN.matcher(path).find();
    }

    public Map<String, String> toAttributes() {
        var attrs = new HashMap<String, String>();
        attrs.put("repo", repo);
        attrs.put("pr-number", String.valueOf(prNumber));
        attrs.put("contributor", contributor);
        attrs.put("lines-changed", String.valueOf(linesChanged));
        attrs.put("changed-paths", toJsonArray(changedPaths));
        attrs.put("modules", toJsonArray(modules));
        attrs.put("languages", toJsonArray(languages));
        attrs.put("has-tests", String.valueOf(hasTests));
        attrs.put("touched-configs", String.valueOf(touchedConfigs));
        return attrs;
    }

    public static PrFeatureVector fromAttributes(Map<String, String> attrs) {
        return new PrFeatureVector(
            attrs.get("repo"),
            Integer.parseInt(attrs.get("pr-number")),
            attrs.get("contributor"),
            Integer.parseInt(attrs.get("lines-changed")),
            fromJsonArray(attrs.get("changed-paths")),
            fromJsonArray(attrs.get("modules")),
            fromJsonArray(attrs.get("languages")),
            Boolean.parseBoolean(attrs.get("has-tests")),
            Boolean.parseBoolean(attrs.get("touched-configs"))
        );
    }

    private static String toJsonArray(Set<String> values) {
        if (values.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        boolean first = true;
        for (var v : values) {
            if (!first) sb.append(",");
            sb.append("\"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static Set<String> fromJsonArray(String json) {
        if (json == null || json.equals("[]")) return Set.of();
        var result = new LinkedHashSet<String>();
        String inner = json.substring(1, json.length() - 1);
        int i = 0;
        while (i < inner.length()) {
            if (inner.charAt(i) != '"') { i++; continue; }
            i++;
            var sb = new StringBuilder();
            while (i < inner.length()) {
                char c = inner.charAt(i);
                if (c == '\\' && i + 1 < inner.length()) {
                    sb.append(inner.charAt(i + 1));
                    i += 2;
                } else if (c == '"') {
                    i++;
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            result.add(sb.toString());
        }
        return Set.copyOf(result);
    }
}
