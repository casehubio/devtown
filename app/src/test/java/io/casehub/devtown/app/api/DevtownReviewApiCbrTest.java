package io.casehub.devtown.app.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.is;

@QuarkusTest
class DevtownReviewApiCbrTest {

    @Test
    void similarCases_returnsEmptyListWhenNoPrecedents() {
        UUID caseId = UUID.randomUUID();
        RestAssured.given()
            .when().get("/api/devtown/reviews/" + caseId + "/similar-cases")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }
}
