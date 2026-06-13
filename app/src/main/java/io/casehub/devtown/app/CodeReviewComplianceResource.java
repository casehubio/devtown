package io.casehub.devtown.app;

import io.casehub.devtown.app.ledger.CodeReviewComplianceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * REST endpoint for code review compliance evidence.
 *
 * <p>Returns a structured compliance report for a given case ID, covering
 * audit chain integrity, trust-based routing decisions, GDPR capabilities,
 * and review SLA status.
 */
@Path("/api/compliance/code-review")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class CodeReviewComplianceResource {

    @Inject CodeReviewComplianceService service;

    @GET
    @Path("/{caseId}")
    public Response getEvidence(
            @PathParam("caseId") UUID caseId,
            @QueryParam("tenancyId") @DefaultValue("default") String tenancyId) {
        return service.findEvidence(caseId, tenancyId)
                .map(evidence -> Response.ok(evidence).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
