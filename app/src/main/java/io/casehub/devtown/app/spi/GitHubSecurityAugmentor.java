package io.casehub.devtown.app.spi;

import io.casehub.devtown.domain.DevtownRoles;
import io.casehub.devtown.github.GitHubOrgApi;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GitHubSecurityAugmentor implements SecurityIdentityAugmentor {

    private static final Logger LOG = Logger.getLogger(GitHubSecurityAugmentor.class);
    private static final long CACHE_TTL_SECONDS = 300;

    @Inject @RestClient GitHubOrgApi orgApi;

    @ConfigProperty(name = "devtown.github.org")
    String githubOrg;

    @ConfigProperty(name = "devtown.github.admin-team", defaultValue = "devtown-admins")
    String adminTeam;

    private final ConcurrentHashMap<String, CachedMembership> cache = new ConcurrentHashMap<>();

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity,
                                          AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> augmentBlocking(identity));
    }

    private SecurityIdentity augmentBlocking(SecurityIdentity identity) {
        String login = identity.getPrincipal().getName();
        CachedMembership membership = resolveMembership(login);
        Set<String> roles = new HashSet<>(identity.getRoles());

        if (membership.isOrgMember) {
            roles.add(DevtownRoles.ENGINEER);
        }
        if (membership.isAdmin) {
            roles.add(DevtownRoles.ADMIN);
        }

        return QuarkusSecurityIdentity.builder(identity)
                .addRoles(roles)
                .addAttribute(SecurityIdentityAttributes.TENANCY_ID, githubOrg)
                .build();
    }

    private CachedMembership resolveMembership(String login) {
        CachedMembership cached = cache.get(login);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        boolean isOrgMember = checkOrgMembership(login);
        boolean isAdmin = isOrgMember && checkTeamMembership(login, adminTeam);
        CachedMembership result = new CachedMembership(isOrgMember, isAdmin, Instant.now());

        // Transient GitHub failure: keep cached membership to avoid revoking access
        if (cached != null && !isOrgMember && cached.isOrgMember) {
            LOG.warnf("GitHub API returned non-member for %s but cache had member — using cached result", login);
            return cached;
        }

        cache.put(login, result);
        return result;
    }

    private boolean checkOrgMembership(String username) {
        try {
            orgApi.checkMembership(githubOrg, username);
            return true;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) return false;
            LOG.warnf("GitHub org membership check failed for %s: HTTP %d %s",
                    username, e.getResponse().getStatus(), e.getMessage());
            return false;
        }
    }

    private boolean checkTeamMembership(String username, String team) {
        try {
            orgApi.checkTeamMembership(githubOrg, team, username);
            return true;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) return false;
            LOG.warnf("GitHub team membership check failed for %s/%s: HTTP %d %s",
                    team, username, e.getResponse().getStatus(), e.getMessage());
            return false;
        }
    }

    private record CachedMembership(boolean isOrgMember, boolean isAdmin, Instant checkedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(checkedAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }
}
