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

import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.evaluator.LambdaExpressionEvaluator;
import io.casehub.devtown.domain.AgentQualification;
import io.casehub.devtown.domain.ReviewDomain;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Fluent DSL companion for the PR review case definition ({@code devtown/pr-review.yaml}).
 *
 * <p>Produces the same logical structure as the YAML but uses
 * {@link LambdaExpressionEvaluator} for binding conditions and goal predicates —
 * enabling pure unit tests without YAML parsing or a Quarkus context.
 *
 * <p>Protocol: {@code case-definition-layers} — every YAML case definition must have
 * a companion fluent Java DSL class. All YAML definitions ⊂ fluent DSL; reverse is
 * not true (DSL supports lambdas, subcases with M-of-N — not expressible in YAML).
 */
public final class PrReviewCaseDefinition {

    private PrReviewCaseDefinition() {}

    public static CaseDefinition build(int humanApprovalThreshold) {
        var codeAnalysisCap   = cap(ReviewDomain.CODE_ANALYSIS, "{ pr: .pr }", "{ codeAnalysis: . }");
        var securityReviewCap = cap(ReviewDomain.SECURITY_REVIEW, "{ pr: .pr, codeAnalysis: .codeAnalysis }", "{ securityReview: { outcome: . } }");
        var archReviewCap     = cap(ReviewDomain.ARCHITECTURE_REVIEW, "{ pr: .pr, codeAnalysis: .codeAnalysis }", "{ architectureReview: { outcome: . } }");
        var styleReviewCap    = cap(ReviewDomain.STYLE_REVIEW, "{ pr: .pr }", "{ styleCheck: { outcome: . } }");
        var testCoverageCap   = cap(ReviewDomain.TEST_COVERAGE, "{ pr: .pr }", "{ testCoverage: { outcome: . } }");
        var perfAnalysisCap   = cap(ReviewDomain.PERFORMANCE_ANALYSIS, "{ pr: .pr }", "{ performanceAnalysis: { outcome: . } }");
        var ciRunnerCap       = cap(AgentQualification.CI_RUNNER, "{ pr: .pr }", "{ ci: { status: . } }");
        var mergeExecutorCap  = cap(AgentQualification.MERGE_EXECUTOR, "{ pr: .pr }", "{}");

        var prApproved = Goal.builder()
            .name("pr-approved")
            .kind(GoalKind.SUCCESS)
            .condition(ctx ->
                (Boolean.FALSE.equals(ctx.getPath("codeAnalysis.securitySensitive")) ||
                    "APPROVED".equals(ctx.getPath("securityReview.outcome"))) &&
                (Boolean.FALSE.equals(ctx.getPath("codeAnalysis.architectureCrossing")) ||
                    "APPROVED".equals(ctx.getPath("architectureReview.outcome"))) &&
                "APPROVED".equals(ctx.getPath("styleCheck.outcome")) &&
                "APPROVED".equals(ctx.getPath("testCoverage.outcome")) &&
                "APPROVED".equals(ctx.getPath("performanceAnalysis.outcome")))
            .build();

        var securityVerified = Goal.builder()
            .name("security-verified")
            .kind(GoalKind.SUCCESS)
            .condition(ctx ->
                Boolean.FALSE.equals(ctx.getPath("codeAnalysis.securitySensitive")) ||
                "APPROVED".equals(ctx.getPath("securityReview.outcome")))
            .build();

        var ciPassing = Goal.builder()
            .name("ci-passing")
            .kind(GoalKind.SUCCESS)
            .condition(ctx -> "passing".equals(ctx.getPath("ci.status")))
            .build();

        var trigger = new ContextChangeTrigger((io.casehub.api.model.evaluator.ExpressionEvaluator) null);

        CaseDefinition def = CaseDefinition.builder()
            .namespace("devtown")
            .name("pr-review")
            .version("1.0.0")
            .completion(GoalExpression.allOf(prApproved, securityVerified, ciPassing))
            .build();

        def.getCapabilities().addAll(List.of(
            codeAnalysisCap, securityReviewCap, archReviewCap,
            styleReviewCap, testCoverageCap, perfAnalysisCap,
            ciRunnerCap, mergeExecutorCap));

        def.getGoals().addAll(List.of(prApproved, securityVerified, ciPassing));

        // Group 1: Entry — fire immediately on PR arrival
        def.getBindings().add(Binding.builder().name("initial-analysis").on(trigger)
            .when(new LambdaExpressionEvaluator(
                ctx -> ctx.get("pr") != null && ctx.get("codeAnalysis") == null))
            .capability(codeAnalysisCap).build());

        def.getBindings().add(Binding.builder().name("run-ci").on(trigger)
            .when(new LambdaExpressionEvaluator(
                ctx -> ctx.get("pr") != null && ctx.get("ci") == null))
            .capability(ciRunnerCap).build());

        // Group 2: Content-driven — fire once analysis results arrive
        def.getBindings().add(Binding.builder().name("security-review").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx ->
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.complete")) &&
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.securitySensitive")) &&
                ctx.get("securityReview") == null))
            .capability(securityReviewCap).build());

        def.getBindings().add(Binding.builder().name("architecture-review").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx ->
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.complete")) &&
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.architectureCrossing")) &&
                ctx.get("architectureReview") == null))
            .capability(archReviewCap).build());

        def.getBindings().add(Binding.builder().name("style-check").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx ->
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.complete")) &&
                ctx.get("styleCheck") == null))
            .capability(styleReviewCap).build());

        def.getBindings().add(Binding.builder().name("test-coverage").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx ->
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.complete")) &&
                ctx.get("testCoverage") == null))
            .capability(testCoverageCap).build());

        def.getBindings().add(Binding.builder().name("performance-analysis").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx ->
                Boolean.TRUE.equals(ctx.getPath("codeAnalysis.complete")) &&
                ctx.get("performanceAnalysis") == null))
            .capability(perfAnalysisCap).build());

        // Group 3: Human gate — threshold-based, HumanTaskTarget (not a capability)
        def.getBindings().add(Binding.builder().name("human-approval").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx -> {
                Object linesChanged = ctx.getPath("pr.linesChanged");
                return linesChanged instanceof Number n &&
                    n.intValue() > humanApprovalThreshold &&
                    ctx.get("humanApproval") == null;
            }))
            .humanTask(HumanTaskTarget.inline()
                .title("PR approval required")
                .candidateGroups(Set.of("pr-reviewers"))
                .expiresIn(Duration.ofHours(24))
                .outputMapping("{ humanApproval: . }")
                .build())
            .build());

        // Group 4: Merge — all checks must pass; human gate conditional on line count
        def.getBindings().add(Binding.builder().name("merge").on(trigger)
            .when(new LambdaExpressionEvaluator(ctx -> {
                Object linesChanged = ctx.getPath("pr.linesChanged");
                boolean humanOk =
                    (linesChanged instanceof Number l && l.intValue() <= humanApprovalThreshold) ||
                    "approved".equals(ctx.getPath("humanApproval.status"));
                return (Boolean.FALSE.equals(ctx.getPath("codeAnalysis.securitySensitive")) ||
                            "APPROVED".equals(ctx.getPath("securityReview.outcome"))) &&
                    (Boolean.FALSE.equals(ctx.getPath("codeAnalysis.architectureCrossing")) ||
                        "APPROVED".equals(ctx.getPath("architectureReview.outcome"))) &&
                    "APPROVED".equals(ctx.getPath("styleCheck.outcome")) &&
                    "APPROVED".equals(ctx.getPath("testCoverage.outcome")) &&
                    "APPROVED".equals(ctx.getPath("performanceAnalysis.outcome")) &&
                    humanOk &&
                    "passing".equals(ctx.getPath("ci.status"));
            }))
            .capability(mergeExecutorCap).build());

        return def;
    }

    private static Capability cap(String name, String inputSchema, String outputSchema) {
        return Capability.builder().name(name).inputSchema(inputSchema).outputSchema(outputSchema).build();
    }
}
