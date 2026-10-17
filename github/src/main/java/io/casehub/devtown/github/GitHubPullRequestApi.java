package io.casehub.devtown.github;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
@Path("/repos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubPullRequestApi {

    @GET
    @Path("/{owner}/{repo}/pulls")
    List<Map<String, Object>> listPullRequests(@PathParam("owner") String owner,
                                                @PathParam("repo") String repo,
                                                @QueryParam("head") String head,
                                                @QueryParam("base") String base,
                                                @QueryParam("state") String state);


    @GET
    @Path("/{owner}/{repo}/pulls")
    List<Map<String, Object>> listPullRequestsByAuthor(
            @PathParam("owner") String owner,
            @PathParam("repo") String repo,
            @QueryParam("creator") String creator,
            @QueryParam("state") String state,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page);

    @POST
    @Path("/{owner}/{repo}/pulls")
    Map<String, Object> createPullRequest(@PathParam("owner") String owner,
                                          @PathParam("repo") String repo,
                                          Map<String, Object> body);

    @GET
    @Path("/{owner}/{repo}/pulls/{pull_number}/files")
    default List<Map<String, Object>> listPullRequestFiles(
            @PathParam("owner") String owner,
            @PathParam("repo") String repo,
            @PathParam("pull_number") int pullNumber,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page) {
        throw new UnsupportedOperationException("listPullRequestFiles");
    }

    @POST
    @Path("/{owner}/{repo}/pulls/{pull_number}/reviews")
    default Map<String, Object> createReview(@PathParam("owner") String owner,
                                     @PathParam("repo") String repo,
                                     @PathParam("pull_number") int pullNumber,
                                     Map<String, Object> body) {
        throw new UnsupportedOperationException("createReview");
    }

    @POST
    @Path("/{owner}/{repo}/pulls/{pull_number}/requested_reviewers")
    default Map<String, Object> requestReviewers(@PathParam("owner") String owner,
                                         @PathParam("repo") String repo,
                                         @PathParam("pull_number") int pullNumber,
                                         Map<String, Object> body) {
        throw new UnsupportedOperationException("requestReviewers");
    }

    @POST
    @Path("/{owner}/{repo}/issues/{issue_number}/comments")
    default Map<String, Object> createIssueComment(@PathParam("owner") String owner,
                                           @PathParam("repo") String repo,
                                           @PathParam("issue_number") int issueNumber,
                                           Map<String, Object> body) {
        throw new UnsupportedOperationException("createIssueComment");
    }

}
