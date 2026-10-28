package io.casehub.devtown.app.spi;

import io.casehub.devtown.github.GitHubOrgApi;
import io.casehub.devtown.github.GitHubTeamMember;
import io.casehub.platform.api.identity.GroupMember;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
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
        when(orgApi.listTeamMembers(eq("casehubio"), eq("pr-reviewers"), anyInt(), eq(1)))
                .thenReturn(List.of(
                        new GitHubTeamMember("alice", 1L),
                        new GitHubTeamMember("bob", 2L)));

        Set<GroupMember> members = provider.membersOf("pr-reviewers", "casehubio");

        assertThat(members).containsExactlyInAnyOrder(
                new GroupMember("alice", "alice"),
                new GroupMember("bob", "bob"));
    }

    @Test
    void membersOf_nonexistentTeam_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("nonexistent"), anyInt(), eq(1)))
                .thenThrow(new WebApplicationException(Response.status(404).build()));

        Set<GroupMember> members = provider.membersOf("nonexistent", "casehubio");

        assertThat(members).isEmpty();
    }

    @Test
    void membersOf_apiError_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("failing-team"), anyInt(), eq(1)))
                .thenThrow(new WebApplicationException(Response.status(500).build()));

        Set<GroupMember> members = provider.membersOf("failing-team", "casehubio");

        assertThat(members).isEmpty();
    }

    @Test
    void membersOf_emptyTeam_returnsEmpty() {
        when(orgApi.listTeamMembers(eq("casehubio"), eq("empty-team"), anyInt(), eq(1)))
                .thenReturn(List.of());

        Set<GroupMember> members = provider.membersOf("empty-team", "casehubio");

        assertThat(members).isEmpty();
    }

    @Test
    void membersOf_paginatesWhenPageFull() {
        List<GitHubTeamMember> page1 = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            page1.add(new GitHubTeamMember("user" + i, (long) i));
        }
        List<GitHubTeamMember> page2 = List.of(
                new GitHubTeamMember("user100", 100L),
                new GitHubTeamMember("user101", 101L));

        when(orgApi.listTeamMembers(eq("casehubio"), eq("big-team"), anyInt(), eq(1)))
                .thenReturn(page1);
        when(orgApi.listTeamMembers(eq("casehubio"), eq("big-team"), anyInt(), eq(2)))
                .thenReturn(page2);

        Set<GroupMember> members = provider.membersOf("big-team", "casehubio");

        assertThat(members).hasSize(102);
        assertThat(members).contains(new GroupMember("user0", "user0"),
                                     new GroupMember("user101", "user101"));
    }


    private void setField(String name, Object value) throws Exception {
        Field f = GitHubGroupMembershipProvider.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(provider, value);
    }
}
