package io.casehub.devtown.app.spi;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
@IfBuildProfile("dev")
public class DevGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupId, String tenancyId) {
        return Set.of();
    }
}
