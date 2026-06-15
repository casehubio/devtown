package io.casehub.devtown.domain.preferences;

import io.casehub.platform.api.preferences.SingleValuePreference;
import java.util.Objects;

public record BooleanPreference(boolean value) implements SingleValuePreference {

    public static BooleanPreference of(final boolean value) {
        return new BooleanPreference(value);
    }

    public static BooleanPreference parse(final String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        return switch (raw.strip().toLowerCase()) {
            case "true" -> new BooleanPreference(true);
            case "false" -> new BooleanPreference(false);
            default -> throw new IllegalArgumentException(
                    "Invalid boolean preference: '" + raw + "' — expected 'true' or 'false'");
        };
    }
}
