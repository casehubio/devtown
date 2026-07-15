package io.casehub.devtown.github;

import java.util.Map;

public record GitRef(String ref, Object object) {
    public String sha() {
        if (object instanceof Map<?, ?> m) {
            return (String) m.get("sha");
        }
        return null;
    }
}
