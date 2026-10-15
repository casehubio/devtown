package io.casehub.devtown.app;

import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestSecurity(user = "dev-user", roles = {"devtown-admin"})
class WebhookReplayResourceTest {

    @Inject PrReviewCaseTracker caseTracker;

    @Test
    void prOpened_startsCase() {
        given()
            .contentType("application/json")
            .body(Map.of("user", "alice", "prNumber", 501, "repo", "casehubio/webapp",
                    "changedPaths", List.of("src/main/java/com/example/Auth.java")))
            .when().post("/api/dev/webhook-replay/pr-opened")
            .then()
            .statusCode(200)
            .body("status", equalTo("accepted"))
            .body("preset", equalTo("pr-opened"))
            .body("user", equalTo("alice"))
            .body("prNumber", equalTo(501));
    }

    @Test
    void reviewApproved_signalsReview() {
        given()
            .contentType("application/json")
            .body(Map.of("user", "alice", "prNumber", 502, "repo", "casehubio/webapp",
                    "changedPaths", List.of("src/main/java/Service.java")))
            .when().post("/api/dev/webhook-replay/pr-opened")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body(Map.of("user", "bob", "prNumber", 502, "repo", "casehubio/webapp"))
            .when().post("/api/dev/webhook-replay/review-approved")
            .then()
            .statusCode(200)
            .body("status", equalTo("accepted"))
            .body("preset", equalTo("review-approved"))
            .body("user", equalTo("bob"))
            .body("result", notNullValue());
    }

    @Test
    void reviewChangesRequested_signalsReview() {
        given()
            .contentType("application/json")
            .body(Map.of("user", "alice", "prNumber", 503, "repo", "casehubio/webapp",
                    "changedPaths", List.of("src/main/java/Service.java")))
            .when().post("/api/dev/webhook-replay/pr-opened")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body(Map.of("user", "charlie", "prNumber", 503, "repo", "casehubio/webapp"))
            .when().post("/api/dev/webhook-replay/review-changes-requested")
            .then()
            .statusCode(200)
            .body("status", equalTo("accepted"))
            .body("preset", equalTo("review-changes-requested"))
            .body("user", equalTo("charlie"));
    }

    @Test
    void ciPass_signalsCiPassing() {
        given()
            .contentType("application/json")
            .body(Map.of("user", "alice", "prNumber", 504, "repo", "casehubio/webapp",
                    "changedPaths", List.of("src/main/java/Service.java")))
            .when().post("/api/dev/webhook-replay/pr-opened")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body(Map.of("prNumber", 504, "repo", "casehubio/webapp"))
            .when().post("/api/dev/webhook-replay/ci-pass")
            .then()
            .statusCode(200)
            .body("status", equalTo("accepted"))
            .body("preset", equalTo("ci-pass"))
            .body("caseId", notNullValue());
    }

    @Test
    void ciPass_noActiveCase_returnsNoCase() {
        given()
            .contentType("application/json")
            .body(Map.of("prNumber", 999, "repo", "casehubio/nonexistent"))
            .when().post("/api/dev/webhook-replay/ci-pass")
            .then()
            .statusCode(200)
            .body("status", equalTo("no_active_case"));
    }
}
