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

import io.casehub.api.model.FailureCategory;
import io.casehub.api.spi.FailureClassificationContext;
import io.casehub.api.spi.FailureClassifier;
import io.casehub.worker.api.WorkerOutcome;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Devtown-specific failure classifier for PR review scenarios. Displaces engine's
 * {@code DefaultFailureClassifier} (@DefaultBean) to provide domain-aware classification
 * of software engineering failure modes.
 */
@ApplicationScoped
public class DevtownFailureClassifier implements FailureClassifier {

  @Override
  public FailureCategory classify(WorkerOutcome<?> outcome, FailureClassificationContext context) {
    String reason = extractReason(outcome);
    if (reason == null) {
      return new FailureCategory.Transient("unknown failure");
    }
    String lower = reason.toLowerCase();

    if (isTransient(lower)) {
      return new FailureCategory.Transient(reason);
    }
    if (isKnowledge(lower)) {
      return new FailureCategory.Knowledge(reason, inferMissingContext(lower));
    }
    if (isInfeasible(lower)) {
      return new FailureCategory.Infeasible(reason);
    }

    if (context.attemptCount() >= context.maxRerouteAttempts()) {
      return new FailureCategory.Infeasible(reason);
    }
    return new FailureCategory.Transient(reason);
  }

  private static boolean isTransient(String reason) {
    return reason.contains("timeout")
        || reason.contains("rate limit")
        || reason.contains("429")
        || reason.contains("503")
        || reason.contains("connection reset")
        || reason.contains("ci failed")
        || reason.contains("ci timeout")
        || reason.contains("pipeline")
        || reason.contains("flaky");
  }

  private static boolean isKnowledge(String reason) {
    return reason.contains("merge conflict")
        || reason.contains("cannot resolve")
        || reason.contains("missing context")
        || reason.contains("hallucination")
        || reason.contains("wrong file")
        || reason.contains("outdated review")
        || reason.contains("stale diff")
        || reason.contains("not enough context");
  }

  private static boolean isInfeasible(String reason) {
    return reason.contains("unsupported language")
        || reason.contains("binary file")
        || reason.contains("too large")
        || reason.contains("exceeds limit")
        || reason.contains("no capability");
  }

  private static String inferMissingContext(String reason) {
    if (reason.contains("merge conflict")) return "conflict resolution context";
    if (reason.contains("stale diff") || reason.contains("outdated review")) return "current PR diff";
    if (reason.contains("hallucination") || reason.contains("wrong file")) return "accurate file mapping";
    return "domain-specific review context";
  }

  private static String extractReason(WorkerOutcome<?> outcome) {
    return switch (outcome) {
      case WorkerOutcome.Declined<?> d -> d.reason();
      case WorkerOutcome.Failed<?> f -> f.reason();
      case WorkerOutcome.Expired<?> e -> e.reason();
      default -> null;
    };
  }
}
