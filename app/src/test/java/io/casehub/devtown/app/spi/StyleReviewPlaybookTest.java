package io.casehub.devtown.app.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.engine.flow.PlaybookWorkerFunction;
import io.casehub.engine.flow.PlaybookWorkerFunctionProvider;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StyleReviewPlaybookTest {

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private final PlaybookWorkerFunctionProvider provider = new PlaybookWorkerFunctionProvider();

  @Test
  void playbook_yaml_is_loadable() throws Exception {
    try (InputStream in =
        getClass().getResourceAsStream("/devtown/playbooks/style-review.yaml")) {
      assertThat(in).isNotNull();
      @SuppressWarnings("unchecked")
      Map<String, Object> content = YAML_MAPPER.readValue(in, Map.class);
      assertThat(content).containsKey("steps");
    }
  }

  @Test
  void provider_handles_playbook_with_steps() throws Exception {
    ObjectNode node = loadPlaybookAsJson();
    assertThat(provider.handles(node)).isTrue();
  }

  @Test
  void provider_creates_worker_function_from_playbook() throws Exception {
    ObjectNode node = loadPlaybookAsJson();
    var fn = provider.create(node);
    assertThat(fn).isInstanceOf(PlaybookWorkerFunction.class);
    PlaybookWorkerFunction pbFn = (PlaybookWorkerFunction) fn;
    assertThat(pbFn.workflow()).isNotNull();
    assertThat(pbFn.compilationResult()).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private ObjectNode loadPlaybookAsJson() throws Exception {
    try (InputStream in =
        getClass().getResourceAsStream("/devtown/playbooks/style-review.yaml")) {
      Map<String, Object> content = YAML_MAPPER.readValue(in, Map.class);
      return JSON_MAPPER.convertValue(content, ObjectNode.class);
    }
  }
}
