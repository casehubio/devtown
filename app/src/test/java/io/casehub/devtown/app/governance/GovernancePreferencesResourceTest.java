package io.casehub.devtown.app.governance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GovernancePreferencesResourceTest {

  @Test
  void returnsDefaultPreferences() {
    given()
        .when()
        .get("/api/governance/preferences")
        .then()
        .statusCode(200)
        .body("refresh.operational", is("10second"))
        .body("refresh.metrics", is("30second"))
        .body("refresh.caseDetail", is("5second"));
  }
}
