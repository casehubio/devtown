package io.casehub.devtown.domain.cbr;

import io.casehub.devtown.domain.preferences.DoublePreference;
import io.casehub.devtown.domain.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class CbrPreferenceKeys {

    private static final String NS = "casehubio.devtown.cbr";

    public static final PreferenceKey<DoublePreference> WEIGHT_FILE_PATHS =
        new PreferenceKey<>(NS, "weight-file-paths", DoublePreference.of(1.0), DoublePreference::parse);
    public static final PreferenceKey<DoublePreference> WEIGHT_MODULES =
        new PreferenceKey<>(NS, "weight-modules", DoublePreference.of(1.5), DoublePreference::parse);
    public static final PreferenceKey<DoublePreference> WEIGHT_LANGUAGES =
        new PreferenceKey<>(NS, "weight-languages", DoublePreference.of(0.5), DoublePreference::parse);
    public static final PreferenceKey<DoublePreference> WEIGHT_CHANGE_SIZE =
        new PreferenceKey<>(NS, "weight-change-size", DoublePreference.of(1.0), DoublePreference::parse);
    public static final PreferenceKey<DoublePreference> WEIGHT_CONTRIBUTOR =
        new PreferenceKey<>(NS, "weight-contributor", DoublePreference.of(0.5), DoublePreference::parse);

    public static final PreferenceKey<IntPreference> K_LIMIT =
        new PreferenceKey<>(NS, "k-limit", IntPreference.of(5), IntPreference::parse);
    public static final PreferenceKey<DoublePreference> MIN_THRESHOLD =
        new PreferenceKey<>(NS, "min-threshold", DoublePreference.of(0.3), DoublePreference::parse);
    public static final PreferenceKey<IntPreference> TIME_WINDOW_DAYS =
        new PreferenceKey<>(NS, "time-window-days", IntPreference.of(180), IntPreference::parse);

    private CbrPreferenceKeys() {}
}
