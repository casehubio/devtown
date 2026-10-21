package io.casehub.devtown.app.spi;

import io.casehub.devtown.github.GitHubOrgApi;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@UnlessBuildProfile("dev")
public class GitHubGroupMembershipProvider implements GroupMembershipProvider {

    private static final Logger LOG = Logger.getLogger(GitHubGroupMembershipProvider.class);

    @Inject @RestClient GitHubOrgApi orgApi;

    @ConfigProperty(name = "devtown.github.org")
    String githubOrg;

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        try {
            List<Map<String, Object>> members = orgApi.listTeamMembers(githubOrg, groupName, 100);
            return members.stream()
                    .map(m -> new GroupMember(
                            (String) m.get("login"),
                            (String) m.get("login")))
                    .collect(Collectors.toSet());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                LOG.debugf("GitHub team not found: %s/%s", githubOrg, groupName);
                return Set.of();
            }
            LOG.warnf("GitHub team lookup failed for %s/%s: %s", githubOrg, groupName, e.getMessage());
            return Set.of();
        }
    }
}
