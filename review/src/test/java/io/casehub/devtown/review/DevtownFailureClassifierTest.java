/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.devtown.review;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.FailureCategory;
import io.casehub.api.spi.FailureClassificationContext;
import io.casehub.worker.api.WorkerOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevtownFailureClassifierTest {

  private final DevtownFailureClassifier classifier = new DevtownFailureClassifier();

  private FailureClassificationContext ctx(int attempt, int maxReroute) {
    return new FailureClassificationContext(
        "worker-1", UUID.randomUUID(), "tenant-1", "review-binding", "code-review",
        attempt, maxReroute);
  }

  @Test
  void ci_timeout_classified_as_transient() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("CI timeout on build step"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Transient.class);
  }

  @Test
  void rate_limit_classified_as_transient() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("429 rate limit exceeded"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Transient.class);
  }

  @Test
  void merge_conflict_classified_as_knowledge() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("merge conflict in src/Main.java"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Knowledge.class);
    assertThat(((FailureCategory.Knowledge) result).missingContext()).isEqualTo("conflict resolution context");
  }

  @Test
  void hallucination_classified_as_knowledge() {
    var result = classifier.classify(new WorkerOutcome.Declined<>("hallucination detected in review"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Knowledge.class);
  }

  @Test
  void unsupported_language_classified_as_infeasible() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("unsupported language: COBOL"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Infeasible.class);
  }

  @Test
  void binary_file_classified_as_infeasible() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("binary file cannot be reviewed"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Infeasible.class);
  }

  @Test
  void expired_timeout_classified_as_transient() {
    var result = classifier.classify(new WorkerOutcome.Expired<>("Watchdog: AGENT_STALE"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Transient.class);
  }

  @Test
  void unknown_failure_at_max_attempts_becomes_infeasible() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("some unknown error"), ctx(3, 3));
    assertThat(result).isInstanceOf(FailureCategory.Infeasible.class);
  }

  @Test
  void unknown_failure_below_max_attempts_is_transient() {
    var result = classifier.classify(new WorkerOutcome.Failed<>("some unknown error"), ctx(1, 3));
    assertThat(result).isInstanceOf(FailureCategory.Transient.class);
  }
}
