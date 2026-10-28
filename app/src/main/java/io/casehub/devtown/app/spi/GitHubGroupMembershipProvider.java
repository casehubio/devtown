package io.casehub.devtown.app.spi;

import io.casehub.devtown.github.GitHubOrgApi;
import io.casehub.devtown.github.GitHubTeamMember;
import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

@ApplicationScoped
@UnlessBuildProfile("dev")
public class GitHubGroupMembershipProvider implements GroupMembershipProvider {

    private static final Logger LOG = Logger.getLogger(GitHubGroupMembershipProvider.class);

    @Inject @RestClient GitHubOrgApi orgApi;

    @ConfigProperty(name = "devtown.github.org")
    String githubOrg;

    private static final int PAGE_SIZE = 100;

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        try {
            Set<GroupMember>       result = new java.util.HashSet<>();
            int                    page   = 1;
            List<GitHubTeamMember> batch;
            do {
                batch = orgApi.listTeamMembers(githubOrg, groupName, PAGE_SIZE, page);
                for (GitHubTeamMember m : batch) {
                    result.add(new GroupMember(m.login(), m.login()));
                }
                page++;
            } while (batch.size() == PAGE_SIZE);
            return result;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                LOG.debugf("GitHub team not found: %s/%s", githubOrg, groupName);
                return Set.of();
            }
            LOG.warnf("GitHub team lookup failed for %s/%s: HTTP %d %s",
                      githubOrg, groupName, e.getResponse().getStatus(), e.getMessage());
            return Set.of();
        }
    }
}
