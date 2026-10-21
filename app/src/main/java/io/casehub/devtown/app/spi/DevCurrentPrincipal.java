package io.casehub.devtown.app.spi;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Set;

@ApplicationScoped
@Alternative
@Priority(200)
@IfBuildProfile("dev")
public class DevCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "dev-operator";
    }

    @Override
    public Set<String> groups() {
        return Set.of("admin");
    }

    @Override
    public String tenancyId() {
        return "dev-tenant";
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}
