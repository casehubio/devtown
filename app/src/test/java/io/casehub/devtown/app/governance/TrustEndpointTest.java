package io.casehub.devtown.app.governance;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class TrustEndpointTest {

    @Test
    void scoreProxyReturnsJsonForUnknownActor() {
        RestAssured.given()
                .when().get("/api/governance/trust/unknown-agent")
                .then()
                .statusCode(200)
                .body("actorId", equalTo("unknown-agent"))
                .body("globalScore", nullValue())
                .body("capabilityScores", anEmptyMap())
                .body("dimensionScores", anEmptyMap());
    }

    @Test
    void routingHistoryReturnsEmptyListForUnknownActor() {
        RestAssured.given()
                .when().get("/api/governance/trust/unknown-agent/routing-history")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void routingDetailReturns404ForNonexistentEntry() {
        RestAssured.given()
                .when().get("/api/governance/trust/unknown-agent/routing-history/00000000-0000-0000-0000-000000000001")
                .then()
                .statusCode(404);
    }
}
