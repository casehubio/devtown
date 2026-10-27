package io.casehub.devtown.app;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.Set;

@Path("/api/userinfo")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("**")
@HandWrittenEndpoint("auth — returns current principal info for the frontend")
public class UserInfoResource {

    @Inject CurrentPrincipal principal;

    @GET
    public Map<String, Object> userInfo() {
        String tenancyId;
        try {
            tenancyId = principal.tenancyId();
        } catch (MissingTenancyException e) {
            tenancyId = "unknown";
        }
        return Map.of(
                "actorId", principal.actorId(),
                "roles", Set.copyOf(principal.roles()),
                "tenancyId", tenancyId,
                "authenticated", principal.isAuthenticated());
    }
}
