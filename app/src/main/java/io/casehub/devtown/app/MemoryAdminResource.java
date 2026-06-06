package io.casehub.devtown.app;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.CaseMemoryStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/memory")
@Consumes(MediaType.APPLICATION_JSON)
public class MemoryAdminResource {

    @Inject
    CaseMemoryStore store;

    @Inject
    CurrentPrincipal principal;

    @POST
    @Path("/erase/contributor")
    public Response eraseContributor(final EraseContributorRequest request) {
        if (request.login() == null || request.login().isBlank()) {
            throw new BadRequestException("login is required");
        }
        try {
            store.eraseEntity("contributor:" + request.login(), principal.tenancyId());
            return Response.noContent().build();
        } catch (UnsupportedOperationException e) {
            return Response.status(501).entity("eraseEntity not supported by the active CaseMemoryStore adapter").build();
        }
    }

    public record EraseContributorRequest(String login) {}
}
