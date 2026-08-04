package io.casehub.devtown.domain.governance;

import io.casehub.devtown.domain.sla.StringPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class GovernancePreferenceKeys {

  private static final String NS = "devtown.governance";

  private GovernancePreferenceKeys() {}

  public static final PreferenceKey<StringPreference> REFRESH_OPERATIONAL =
      new PreferenceKey<>(NS, "refresh-operational", StringPreference.of("10second"), StringPreference::parse);

  public static final PreferenceKey<StringPreference> REFRESH_METRICS =
      new PreferenceKey<>(NS, "refresh-metrics", StringPreference.of("30second"), StringPreference::parse);

  public static final PreferenceKey<StringPreference> REFRESH_CASE_DETAIL =
      new PreferenceKey<>(NS, "refresh-case-detail", StringPreference.of("5second"), StringPreference::parse);
}
