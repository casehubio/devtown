package io.casehub.devtown.github;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "github-api")
@Path("/repos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubRepoApi {

    @GET
    @Path("/{owner}/{repo}")
    Map<String, Object> getRepository(@PathParam("owner") String owner,
                                       @PathParam("repo") String repo);

    @GET
    @Path("/{owner}/{repo}/contributors")
    List<Map<String, Object>> listContributors(@PathParam("owner") String owner,
                                                @PathParam("repo") String repo,
                                                @QueryParam("per_page") int perPage,
                                                @QueryParam("anon") String anon);
}
