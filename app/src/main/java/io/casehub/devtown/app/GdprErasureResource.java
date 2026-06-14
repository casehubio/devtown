package io.casehub.devtown.app;

import io.casehub.devtown.app.ledger.GdprErasureService;
import io.casehub.devtown.review.compliance.ErasureReceipt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/actors/{actorId}/erasure")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GdprErasureResource {

    @Inject GdprErasureService service;

    @POST
    public ErasureReceipt erase(
            @PathParam("actorId") final String actorId,
            @QueryParam("tenancyId") @DefaultValue("default") final String tenancyId,
            final ErasureRequest request) {
        return service.erase(actorId, tenancyId, request != null ? request.reason() : null);
    }

    public record ErasureRequest(String reason) {}
}
