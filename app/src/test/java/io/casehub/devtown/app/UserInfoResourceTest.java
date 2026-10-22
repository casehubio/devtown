package io.casehub.devtown.app;

import io.casehub.devtown.domain.DevtownRoles;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserInfoResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal.reset();
    }

    @Test
    @TestSecurity(user = "alice", roles = {DevtownRoles.ENGINEER})
    void authenticatedUser_returnsUserInfo() {
        principal.setActorId("alice");
        principal.setGroups(Set.of(DevtownRoles.ENGINEER));

        given()
            .when().get("/api/userinfo")
            .then()
                .statusCode(200)
                .body("actorId", equalTo("alice"))
                .body("authenticated", equalTo(true))
                .body("roles", hasItem(DevtownRoles.ENGINEER));
    }

    @Test
    @TestSecurity(user = "admin-user", roles = {DevtownRoles.ADMIN, DevtownRoles.ENGINEER})
    void adminUser_returnsAllRoles() {
        principal.setActorId("admin-user");
        principal.setGroups(Set.of(DevtownRoles.ADMIN, DevtownRoles.ENGINEER));

        given()
            .when().get("/api/userinfo")
            .then()
                .statusCode(200)
                .body("actorId", equalTo("admin-user"))
                .body("roles", hasItems(DevtownRoles.ADMIN, DevtownRoles.ENGINEER));
    }

    @Test
    @TestSecurity(user = "test-user", roles = {DevtownRoles.ENGINEER})
    void tenancyId_returnedInResponse() {
        principal.setActorId("test-user");
        principal.setGroups(Set.of(DevtownRoles.ENGINEER));

        given()
            .when().get("/api/userinfo")
            .then()
                .statusCode(200)
                .body("tenancyId", notNullValue());
    }

    // unauthenticated → 401 is enforced by %prod.quarkus.http.auth.permission.api-default
    // which is not active in test profile. DevtownRestSecurityTest covers the annotated endpoint auth.
}
