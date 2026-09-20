package io.casehub.devtown.app.api;

import io.casehub.devtown.app.CodeReviewComplianceResource;
import io.casehub.devtown.app.GdprErasureResource;
import io.casehub.devtown.app.MemoryAdminResource;
import io.casehub.devtown.review.compliance.ErasureReceipt;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "devtown/compliance", basePath = "/api/devtown/compliance")
@ApplicationScoped
public class DevtownComplianceApi {

    @Inject CodeReviewComplianceResource complianceResource;
    @Inject GdprErasureResource erasureResource;
    @Inject MemoryAdminResource memoryAdminResource;

    @PlatformQuery("Get code review compliance evidence for a case")
    @RestPath("/code-review/{caseId}")
    public Object getEvidence(@PathParam UUID caseId) {
        return complianceResource.getEvidence(caseId).getEntity();
    }

    @PlatformMutation("Request GDPR erasure for an actor")
    @RestPath("/erasure/{actorId}")
    public ErasureReceipt erase(@PathParam String actorId, String reason) {
        return erasureResource.erase(actorId, new GdprErasureResource.ErasureRequest(reason));
    }

    @PlatformMutation("Erase contributor memory (GDPR)")
    @RestPath("/memory/erase-contributor")
    public Object eraseContributor(String login) {
        return memoryAdminResource.eraseContributor(
            new MemoryAdminResource.EraseContributorRequest(login)).getEntity();
    }
}
