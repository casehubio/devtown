package io.casehub.devtown.app.governance;

import io.casehub.devtown.domain.governance.GovernancePreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/governance/preferences")
@Produces(MediaType.APPLICATION_JSON)
@jakarta.annotation.security.PermitAll
public class GovernancePreferencesResource {

  @Inject PreferenceProvider preferenceProvider;

  @GET
  public Map<String, Map<String, String>> get() {
    Preferences prefs = preferenceProvider.resolve(SettingsScope.root("casehubio"));
    return Map.of(
        "refresh",
        Map.of(
            "operational",
                prefs.getOrDefault(GovernancePreferenceKeys.REFRESH_OPERATIONAL).value(),
            "metrics",
                prefs.getOrDefault(GovernancePreferenceKeys.REFRESH_METRICS).value(),
            "caseDetail",
                prefs.getOrDefault(GovernancePreferenceKeys.REFRESH_CASE_DETAIL).value()));
  }
}
