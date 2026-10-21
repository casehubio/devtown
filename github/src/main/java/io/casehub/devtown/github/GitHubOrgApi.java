package io.casehub.devtown.github;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "github-api")
@RegisterClientHeaders(GitHubAuthHeadersFactory.class)
@Path("/orgs")
@Produces(MediaType.APPLICATION_JSON)
public interface GitHubOrgApi {

    @GET
    @Path("/{org}/members/{username}")
    void checkMembership(@PathParam("org") String org,
                         @PathParam("username") String username);

    @GET
    @Path("/{org}/teams/{team}/members")
    List<Map<String, Object>> listTeamMembers(@PathParam("org") String org,
                                               @PathParam("team") String team,
                                               @QueryParam("per_page") int perPage);

    @GET
    @Path("/{org}/teams/{team}/members/{username}")
    void checkTeamMembership(@PathParam("org") String org,
                             @PathParam("team") String team,
                             @PathParam("username") String username);
}
