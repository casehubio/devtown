package io.casehub.devtown.app.spi;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "test-user", roles = {"devtown-engineer"})
class CdiDisplacementTest {

    @Inject CurrentPrincipal principal;
    @Inject GroupMembershipProvider membershipProvider;

    @Test
    void principalIsFixedCurrentPrincipal_notDevCurrentPrincipal() {
        assertInstanceOf(FixedCurrentPrincipal.class, principal,
            "In test profile, FixedCurrentPrincipal (@Priority 200) should win over SICP (@Priority 100)");
        assertNotEquals("dev-operator", principal.actorId(),
            "DevCurrentPrincipal should be invisible in test profile");
    }

    @Test
    void groupMembershipProviderIsNotDevProvider() {
        assertNotEquals(DevGroupMembershipProvider.class, membershipProvider.getClass(),
            "DevGroupMembershipProvider should be invisible in test profile");
    }
}
