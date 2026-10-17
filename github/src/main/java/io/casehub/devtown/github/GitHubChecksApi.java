package io.casehub.devtown.github;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "github-api")
@RegisterClientHeaders(GitHubAuthHeadersFactory.class)
@Path("/repos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubChecksApi {

    @GET
    @Path("/{owner}/{repo}/commits/{ref}/check-suites")
    CheckSuitesResponse listCheckSuites(
        @PathParam("owner") String owner,
        @PathParam("repo") String repo,
        @PathParam("ref") String ref,
        @QueryParam("per_page") int perPage);

    @POST
    @Path("/{owner}/{repo}/check-runs")
    default Map<String, Object> createCheckRun(@PathParam("owner") String owner,
                                       @PathParam("repo") String repo,
                                       Map<String, Object> body) {
        throw new UnsupportedOperationException("createCheckRun");
    }

    @PATCH
    @Path("/{owner}/{repo}/check-runs/{check_run_id}")
    default Map<String, Object> updateCheckRun(@PathParam("owner") String owner,
                                       @PathParam("repo") String repo,
                                       @PathParam("check_run_id") long checkRunId,
                                       Map<String, Object> body) {
        throw new UnsupportedOperationException("updateCheckRun");
    }

}
