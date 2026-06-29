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
package io.casehub.devtown.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.converter.CaseDefinitionYamlMapper;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for mutually exclusive merge bindings in pr-review.yaml.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>When {@code policy.mergeQueueEnabled == true} AND all approvals met AND {@code enqueueResult == null},
 *       the {@code enqueue-for-merge} binding fires
 *   <li>When {@code policy.mergeQueueEnabled != true} AND all approvals met,
 *       the {@code merge-direct} binding fires
 *   <li>When {@code enqueueResult != null}, the {@code enqueue-for-merge} binding does NOT re-fire
 * </ul>
 */
class PrReviewMergeQueueRoutingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static CaseDefinition def;
    private static Scope rootScope;

    @BeforeAll
    static void loadDefinition() throws IOException {
        def = CaseDefinitionYamlMapper.load(
            PrReviewMergeQueueRoutingTest.class.getClassLoader()
                .getResourceAsStream("devtown/pr-review.yaml"));
        rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
    }

    private static String bindingCondition(String bindingName) {
        return def.getBindings().stream()
            .filter(b -> b.getName().equals(bindingName))
            .findFirst()
            .map(b -> ((JQExpressionEvaluator) b.getWhen()).expression())
            .orElseThrow(() -> new AssertionError("Binding not found: " + bindingName));
    }

    private static boolean eval(String jqExpr, Map<String, Object> context) {
        try {
            JsonNode node = MAPPER.valueToTree(context);
            Scope childScope = Scope.newChildScope(rootScope);
            JsonQuery query = JsonQuery.compile(jqExpr, Versions.JQ_1_6);
            List<JsonNode> out = new ArrayList<>();
            query.apply(childScope, node, out::add);
            return !out.isEmpty() && out.getFirst().isBoolean() && out.getFirst().asBoolean();
        } catch (Exception e) {
            throw new RuntimeException("JQ evaluation failed for: " + jqExpr, e);
        }
    }

    /**
     * Creates a fully approved PR review context (all checks passed, no merge yet).
     */
    private static Map<String, Object> approvedContext(boolean mergeQueueEnabled) {
        Map<String, Object> ctx = new HashMap<>();
        Map<String, Object> prMap = new HashMap<>();
        prMap.put("status", "open");
        prMap.put("linesChanged", 100);
        ctx.put("pr", prMap);

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put("humanApprovalThreshold", 500);
        policyMap.put("mergeQueueEnabled", mergeQueueEnabled);
        ctx.put("policy", policyMap);

        Map<String, Object> codeAnalysisMap = new HashMap<>();
        codeAnalysisMap.put("securitySensitive", false);
        codeAnalysisMap.put("architectureCrossing", false);
        ctx.put("codeAnalysis", codeAnalysisMap);

        ctx.put("securityReview", Map.of("outcome", "APPROVED"));
        ctx.put("architectureReview", Map.of("outcome", "APPROVED"));
        ctx.put("styleCheck", Map.of("outcome", "APPROVED"));
        ctx.put("testCoverage", Map.of("outcome", "APPROVED"));
        ctx.put("performanceAnalysis", Map.of("outcome", "APPROVED"));

        Map<String, Object> ciMap = new HashMap<>();
        ciMap.put("status", "passing");
        ctx.put("ci", ciMap);
        // merge_sha is null (not merged yet)
        // enqueueResult is null (not enqueued yet)
        return ctx;
    }

    @Nested class EnqueueForMerge {
        @Test void fires_whenMergeQueueEnabledAndApproved() {
            var ctx = approvedContext(true);
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isTrue();
        }

        @Test void doesNotFire_whenMergeQueueDisabled() {
            var ctx = approvedContext(false);
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenEnqueueResultAlreadyPresent() {
            var ctx = approvedContext(true);
            ctx.put("enqueueResult", Map.of("status", "enqueued"));
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenMergeShaAlreadyPresent() {
            var ctx = approvedContext(true);
            ctx.put("merge_sha", "abc123def456");
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenPrMerged() {
            var ctx = approvedContext(true);
            ((Map<String, Object>) ctx.get("pr")).put("status", "merged");
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenCiNotPassing() {
            var ctx = approvedContext(true);
            ((Map<String, Object>) ctx.get("ci")).put("status", "failing");
            assertThat(eval(bindingCondition("enqueue-for-merge"), ctx)).isFalse();
        }
    }

    @Nested class MergeDirect {
        @Test void fires_whenMergeQueueDisabledAndApproved() {
            var ctx = approvedContext(false);
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isTrue();
        }

        @Test void fires_whenMergeQueueEnabledFalse() {
            var ctx = approvedContext(false);
            // Explicitly set to false (not just != true)
            ((Map<String, Object>) ctx.get("policy")).put("mergeQueueEnabled", false);
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isTrue();
        }

        @Test void doesNotFire_whenMergeQueueEnabled() {
            var ctx = approvedContext(true);
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenMergeShaAlreadyPresent() {
            var ctx = approvedContext(false);
            ctx.put("merge_sha", "abc123def456");
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenPrMerged() {
            var ctx = approvedContext(false);
            ((Map<String, Object>) ctx.get("pr")).put("status", "merged");
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isFalse();
        }

        @Test void doesNotFire_whenCiNotPassing() {
            var ctx = approvedContext(false);
            ((Map<String, Object>) ctx.get("ci")).put("status", "failing");
            assertThat(eval(bindingCondition("merge-direct"), ctx)).isFalse();
        }
    }

    @Nested class MutualExclusivity {
        @Test void enqueueForMerge_andMergeDirect_neverBothFire() {
            // When merge queue enabled, only enqueue-for-merge fires
            var enabledCtx = approvedContext(true);
            assertThat(eval(bindingCondition("enqueue-for-merge"), enabledCtx)).isTrue();
            assertThat(eval(bindingCondition("merge-direct"), enabledCtx)).isFalse();

            // When merge queue disabled, only merge-direct fires
            var disabledCtx = approvedContext(false);
            assertThat(eval(bindingCondition("enqueue-for-merge"), disabledCtx)).isFalse();
            assertThat(eval(bindingCondition("merge-direct"), disabledCtx)).isTrue();
        }
    }
}
