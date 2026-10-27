package io.casehub.devtown.app.evolution;

import jakarta.annotation.Nullable;

public record ConductorOriginResponse(
    boolean conductorDriven,
    @Nullable String streamCategory) {

  public static ConductorOriginResponse notConductorDriven() {
    return new ConductorOriginResponse(false, null);
  }

  public static ConductorOriginResponse conductorDriven(String streamCategory) {
    return new ConductorOriginResponse(true, streamCategory);
  }
}
