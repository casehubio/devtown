package io.casehub.devtown.domain.memory;

import java.util.List;

public final class ModulePathNormalizer {
    public static final String ROOT = "(root)";

    public static List<String> normalize(List<String> changedPaths) {
        return changedPaths.stream()
            .map(ModulePathNormalizer::toModule)
            .distinct()
            .toList();
    }

    private static String toModule(String path) {
        int srcIdx = path.indexOf("/src/");
        if (srcIdx <= 0) return ROOT;
        return path.substring(0, srcIdx);
    }

    private ModulePathNormalizer() {}
}
