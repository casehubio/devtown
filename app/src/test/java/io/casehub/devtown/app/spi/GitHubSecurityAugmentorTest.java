package io.casehub.devtown.app.spi;

import io.casehub.devtown.domain.DevtownRoles;
import io.casehub.devtown.github.GitHubOrgApi;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubSecurityAugmentorTest {

    private GitHubSecurityAugmentor augmentor;
    private GitHubOrgApi orgApi;

    @BeforeEach
    void setUp() throws Exception {
        augmentor = new GitHubSecurityAugmentor();
        orgApi = mock(GitHubOrgApi.class);
        setField("orgApi", orgApi);
        setField("githubOrg", "casehubio");
        setField("adminTeam", "devtown-admins");
    }

    @Test
    void orgMember_getsEngineerRole() {
        doThrow(new WebApplicationException(Response.status(404).build()))
                .when(orgApi).checkTeamMembership(eq("casehubio"), eq("devtown-admins"), eq("alice"));
        SecurityIdentity result = augmentSync(identity("alice"));

        assertThat(result.getRoles()).contains(DevtownRoles.ENGINEER);
        assertThat(result.getRoles()).doesNotContain(DevtownRoles.ADMIN);
    }

    @Test
    void adminTeamMember_getsAdminAndEngineerRoles() {
        // checkMembership and checkTeamMembership both succeed (Mockito default: no-op void)
        SecurityIdentity result = augmentSync(identity("bob"));

        assertThat(result.getRoles()).contains(DevtownRoles.ENGINEER, DevtownRoles.ADMIN);
    }

    @Test
    void nonMember_getsNoDevtownRoles() {
        doThrow(new WebApplicationException(Response.status(404).build()))
                .when(orgApi).checkMembership(eq("casehubio"), eq("outsider"));
        SecurityIdentity result = augmentSync(identity("outsider"));

        assertThat(result.getRoles()).doesNotContain(DevtownRoles.ENGINEER, DevtownRoles.ADMIN);
    }

    @Test
    void anonymous_passesThrough_noApiCall() {
        SecurityIdentity anon = QuarkusSecurityIdentity.builder()
                .setAnonymous(true)
                .setPrincipal(new SimplePrincipal("anonymous"))
                .build();

        SecurityIdentity result = augmentor.augment(anon, blockingContext()).await().indefinitely();

        assertThat(result.isAnonymous()).isTrue();
        verify(orgApi, never()).checkMembership(anyString(), anyString());
    }

    @Test
    void tenancyId_setToConfiguredOrg() {
        SecurityIdentity result = augmentSync(identity("carol"));

        assertThat(result.<String>getAttribute(SecurityIdentityAttributes.TENANCY_ID))
                .isEqualTo("casehubio");
    }

    @Test
    void cachedResult_noSecondApiCall() {
        augmentSync(identity("dave"));
        augmentSync(identity("dave"));

        verify(orgApi, times(1)).checkMembership(eq("casehubio"), eq("dave"));
    }

    @Test
    void transientFailure_preservesCachedMembership() {
        SecurityIdentity first = augmentSync(identity("eve"));
        assertThat(first.getRoles()).contains(DevtownRoles.ENGINEER);

        injectExpiredCache("eve", true, false);

        doThrow(new WebApplicationException(Response.status(404).build()))
                .when(orgApi).checkMembership(eq("casehubio"), eq("eve"));
        SecurityIdentity second = augmentSync(identity("eve"));

        assertThat(second.getRoles()).as("transient failure should preserve cached membership")
                .contains(DevtownRoles.ENGINEER);
    }

    @Test
    void existingRoles_preserved() {
        SecurityIdentity input = QuarkusSecurityIdentity.builder()
                .setPrincipal(new SimplePrincipal("frank"))
                .addRole("pre-existing-role")
                .build();
        SecurityIdentity result = augmentSync(input);

        assertThat(result.getRoles()).contains("pre-existing-role", DevtownRoles.ENGINEER);
    }

    @Test
    void coldCache_githubServerError_throwsException() {
        doThrow(new WebApplicationException(Response.status(500).build()))
                .when(orgApi).checkMembership(eq("casehubio"), eq("newuser"));

        assertThatThrownBy(() -> augmentSync(identity("newuser")))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void warmCache_githubServerError_usesCachedResult() {
        SecurityIdentity first = augmentSync(identity("eve"));
        assertThat(first.getRoles()).contains(DevtownRoles.ENGINEER);

        injectExpiredCache("eve", true, false);

        doThrow(new WebApplicationException(Response.status(500).build()))
                .when(orgApi).checkMembership(eq("casehubio"), eq("eve"));
        SecurityIdentity second = augmentSync(identity("eve"));

        assertThat(second.getRoles()).as("server error should preserve cached membership")
                .contains(DevtownRoles.ENGINEER);
    }

    @Test
    void adminCheck_skippedForNonOrgMember() {
        doThrow(new WebApplicationException(Response.status(404).build()))
                .when(orgApi).checkMembership(eq("casehubio"), eq("ghost"));
        augmentSync(identity("ghost"));

        verify(orgApi, never()).checkTeamMembership(anyString(), anyString(), anyString());
    }

    private SecurityIdentity identity(String login) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new SimplePrincipal(login))
                .build();
    }

    private SecurityIdentity augmentSync(SecurityIdentity identity) {
        return augmentor.augment(identity, blockingContext()).await().indefinitely();
    }

    private AuthenticationRequestContext blockingContext() {
        return supplier -> io.smallrye.mutiny.Uni.createFrom().item(supplier.get());
    }

    private void setField(String name, Object value) throws Exception {
        Field f = GitHubSecurityAugmentor.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(augmentor, value);
    }

    @SuppressWarnings("unchecked")
    private void injectExpiredCache(String login, boolean isOrgMember, boolean isAdmin) {
        try {
            Field cacheField = GitHubSecurityAugmentor.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            var cache = (java.util.concurrent.ConcurrentHashMap<String, Object>) cacheField.get(augmentor);
            var ctor = Class.forName(GitHubSecurityAugmentor.class.getName() + "$CachedMembership")
                    .getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Object expired = ctor.newInstance(isOrgMember, isAdmin, Instant.now().minusSeconds(600));
            cache.put(login, expired);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record SimplePrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }
}
