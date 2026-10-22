package io.casehub.devtown.app.spi;

import io.casehub.devtown.github.GitHubOrgApi;
import io.casehub.platform.api.identity.GroupMember;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubGroupMembershipProviderTest {

    private GitHubGroupMembershipProvider provider;
    private GitHubOrgApi orgApi;

    @BeforeEach
    void setUp() throws Exception {
        provider = new GitHubGroupMembershipProvider();
        orgApi = mock(GitHubOrgApi.class);
        setField("orgApi", orgApi);
        setField("githubOrg", "casehubio");
    }

    @Test
    void membersOf_returnsGroupMembers() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("pr-reviewers"), anyInt()))
                .thenReturn(List.of(
                        Map.of("login", "alice", "id", 1),
                        Map.of("login", "bob", "id", 2)));

        Set<GroupMember> members = provider.membersOf("pr-reviewers", "casehubio");

        assertThat(members).containsExactlyInAnyOrder(
                new GroupMember("alice", "alice"),
                new GroupMember("bob", "bob"));
    }

    @Test
    void membersOf_nonexistentTeam_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("nonexistent"), anyInt()))
                .thenThrow(new WebApplicationException(Response.status(404).build()));

        Set<GroupMember> members = provider.membersOf("nonexistent", "casehubio");

        assertThat(members).isEmpty();
    }

    @Test
    void membersOf_apiError_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("failing-team"), anyInt()))
                .thenThrow(new WebApplicationException(Response.status(500).build()));

        Set<GroupMember> members = provider.membersOf("failing-team", "casehubio");

        assertThat(members).isEmpty();
    }

    @Test
    void membersOf_emptyTeam_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("empty-team"), anyInt()))
                .thenReturn(List.of());

        Set<GroupMember> members = provider.membersOf("empty-team", "casehubio");

        assertThat(members).isEmpty();
    }

    private void setField(String name, Object value) throws Exception {
        Field f = GitHubGroupMembershipProvider.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(provider, value);
    }
}
