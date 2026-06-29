package io.casehub.devtown.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubPullRequestEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OPENED_EVENT = """
            {
              "action": "opened",
              "number": 42,
              "pull_request": {
                "head": { "sha": "abc123" },
                "base": { "ref": "main" },
                "user": { "login": "octocat" },
                "draft": false,
                "merged": false,
                "additions": 100,
                "deletions": 50,
                "changed_files": 5
              },
              "repository": {
                "full_name": "casehubio/devtown"
              },
              "sender": { "login": "octocat" },
              "installation": { "id": 12345 }
            }
            """;

    @Test
    void parsesAction() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.action()).isEqualTo("opened");
    }

    @Test
    void parsesNumber() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.number()).isEqualTo(42);
    }

    @Test
    void parsesPullRequestHead() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().head().sha()).isEqualTo("abc123");
    }

    @Test
    void parsesPullRequestBase() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().base().ref()).isEqualTo("main");
    }

    @Test
    void parsesPullRequestUser() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().user().login()).isEqualTo("octocat");
    }

    @Test
    void parsesDraftFlag() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().draft()).isFalse();
    }

    @Test
    void parsesLinesChanged() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().additions()).isEqualTo(100);
        assertThat(event.pull_request().deletions()).isEqualTo(50);
    }

    @Test
    void parsesRepository() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event.repository().full_name()).isEqualTo("casehubio/devtown");
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        var event = MAPPER.readValue(OPENED_EVENT, GitHubPullRequestEvent.class);
        assertThat(event).isNotNull();
    }

    @Test
    void parsesMergedFlag() throws Exception {
        var merged = OPENED_EVENT.replace("\"merged\": false", "\"merged\": true");
        var event = MAPPER.readValue(merged, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().merged()).isTrue();
    }

    @Test
    void parsesDraftTrue() throws Exception {
        var draft = OPENED_EVENT.replace("\"draft\": false", "\"draft\": true");
        var event = MAPPER.readValue(draft, GitHubPullRequestEvent.class);
        assertThat(event.pull_request().draft()).isTrue();
    }

    @Test
    void parsesLabeledEvent() throws Exception {
        String json = "{\"action\":\"labeled\",\"number\":42,\"label\":{\"name\":\"merge-ready\"},\"pull_request\":{\"head\":{\"sha\":\"abc\"},\"base\":{\"ref\":\"main\"},\"user\":{\"login\":\"octocat\"},\"draft\":false,\"merged\":false,\"additions\":10,\"deletions\":5,\"changed_files\":1},\"repository\":{\"full_name\":\"casehubio/devtown\"}}";
        var event = MAPPER.readValue(json, GitHubPullRequestEvent.class);
        assertThat(event.label()).isNotNull();
        assertThat(event.label().name()).isEqualTo("merge-ready");
    }

    @Test
    void parsesOpenedEvent_labelIsNull() throws Exception {
        String json = "{\"action\":\"opened\",\"number\":42,\"pull_request\":{\"head\":{\"sha\":\"abc\"},\"base\":{\"ref\":\"main\"},\"user\":{\"login\":\"octocat\"},\"draft\":false,\"merged\":false,\"additions\":10,\"deletions\":5,\"changed_files\":1},\"repository\":{\"full_name\":\"casehubio/devtown\"}}";
        var event = MAPPER.readValue(json, GitHubPullRequestEvent.class);
        assertThat(event.label()).isNull();
    }
}
