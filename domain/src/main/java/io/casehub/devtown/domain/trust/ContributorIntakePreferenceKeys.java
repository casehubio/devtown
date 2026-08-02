package io.casehub.devtown.domain.trust;

import io.casehub.devtown.domain.preferences.DoublePreference;
import io.casehub.devtown.domain.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class ContributorIntakePreferenceKeys {

    public static final PreferenceKey<DoublePreference> FAST_TRACK_THRESHOLD =
        new PreferenceKey<>("casehubio.devtown.contributor-intake",
            "fast-track-threshold",
            DoublePreference.of(0.75), DoublePreference::parse);

    public static final PreferenceKey<IntPreference> FAST_TRACK_MIN_OBSERVATIONS =
        new PreferenceKey<>("casehubio.devtown.contributor-intake",
            "fast-track-min-observations",
            IntPreference.of(10), IntPreference::parse);

    public static final PreferenceKey<DoublePreference> STANDARD_THRESHOLD =
        new PreferenceKey<>("casehubio.devtown.contributor-intake",
            "standard-threshold",
            DoublePreference.of(0.50), DoublePreference::parse);

    public static final PreferenceKey<IntPreference> STANDARD_MIN_OBSERVATIONS =
        new PreferenceKey<>("casehubio.devtown.contributor-intake",
            "standard-min-observations",
            IntPreference.of(3), IntPreference::parse);

    private ContributorIntakePreferenceKeys() {}
}
