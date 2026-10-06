package io.casehub.devtown.app.scenario;

import io.casehub.devtown.app.PrReviewCaseHub;
import io.casehub.devtown.app.mcp.PrReviewCaseTracker;
import io.casehub.devtown.merge.MergeQueuePort;
import io.casehub.devtown.review.PrPayload;
import io.casehub.devtown.review.PrReviewApplicationService;
import io.casehub.devtown.review.PrReviewSubmission;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.spi.WorkItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Path("/scenario")
@jakarta.annotation.security.PermitAll
@io.casehub.platform.api.mcp.HandWrittenEndpoint("Scenario step controller for demo walkthrough")
public class PlaybookResource {

    @Inject PrReviewApplicationService reviewService;
    @Inject MergeQueuePort mergeQueue;
    @Inject PrReviewCaseTracker caseTracker;
    @Inject PrReviewCaseHub caseHub;
    @Inject WorkItemStore workItemStore;
    @Inject WorkItemOperations workItemOperations;

    private int currentStep = 0;
    private boolean paused = true;
    private final List<StepResult> history = new ArrayList<>();

    record StepResult(int index, String phase, String label, String status, Object result) {}

    private record StepDef(String phase, String label, String narrative) {}

    private static final List<StepDef> STEPS = List.of(
        new StepDef("PR Submission", "Submit PR #101 — Security auth refactor (alice, 460 lines)",
            "Security-sensitive paths detected. Expect security-review binding."),
        new StepDef("PR Submission", "Submit PR #102 — Rename variables (bob, 50 lines)",
            "Small change, below human-approval threshold."),
        new StepDef("PR Submission", "Submit PR #103 — Extract payment module (charlie, 1340 lines)",
            "Large change with architecture crossing. Expect human-approval gate."),
        new StepDef("Analysis", "Check queue status and routing",
            "Observe how the engine routed each PR based on code content."),
        new StepDef("Review Completion", "Approve PR #102 (simple rename)",
            "Simple PR should advance fastest toward merge readiness."),
        new StepDef("Review Completion", "Approve PR #101 (security)",
            "Security review accepted."),
        new StepDef("Human Gate", "Claim and decide human-approval WorkItem (PR #103)",
            "PR #103 triggered a human-approval gate. Claim it, then approve."),
        new StepDef("Review Completion", "Approve PR #103 (architecture)",
            "Architecture review accepted. Human gate already cleared."),
        new StepDef("CI Pass", "Signal CI passing for all PRs",
            "CI green for all 3 PRs."),
        new StepDef("Merge Queue", "Enqueue approved PRs",
            "Trust-weighted ordering determines batch position."),
        new StepDef("Trust Evolution", "Review trust scores",
            "Observe how review outcomes updated trust scores.")
    );

    @GET
    @Path("/state")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> state() {
        StepDef step = currentStep < STEPS.size() ? STEPS.get(currentStep) : null;
        var outline = new ArrayList<Map<String, Object>>();
        String currentPhase = null;
        Map<String, Object> phaseNode = null;
        List<Map<String, Object>> phaseChildren = null;
        for (int i = 0; i < STEPS.size(); i++) {
            StepDef s = STEPS.get(i);
            if (!s.phase().equals(currentPhase)) {
                currentPhase = s.phase();
                phaseChildren = new ArrayList<>();
                phaseNode = new LinkedHashMap<>();
                phaseNode.put("label", currentPhase);
                phaseNode.put("target", null);
                phaseNode.put("children", phaseChildren);
                outline.add(phaseNode);
            }
            String status = i < currentStep ? "done" : i == currentStep ? "current" : "pending";
            phaseChildren.add(Map.of("label", s.label(), "target", String.valueOf(i), "status", status, "children", List.of()));
        }
        var state = new LinkedHashMap<String, Object>();
        state.put("scenario", "pr-lifecycle");
        state.put("chapter", step != null ? step.phase() : "Complete");
        state.put("section", null);
        state.put("step", step != null ? step.label() : null);
        state.put("paused", paused);
        state.put("speed", 1.0);
        state.put("progress", STEPS.isEmpty() ? 1.0 : (double) currentStep / STEPS.size());
        state.put("content", step != null ? Map.of("type", "inline", "markdown", step.narrative()) : null);
        state.put("slides", null);
        state.put("outline", outline);
        state.put("totalSteps", STEPS.size());
        state.put("currentStep", currentStep);
        state.put("history", history);
        return state;
    }

    @POST
    @Path("/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> start() {
        currentStep = 0;
        paused      = true;
        history.clear();
        cleanPreviousRun();
        return state();
    }

    @POST
    @Path("/step")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> step() {
        if (currentStep >= STEPS.size()) {
            return Map.of("status", "complete", "message", "All steps executed");
        }
        Object result = executeStep(currentStep);
        StepDef def = STEPS.get(currentStep);
        history.add(new StepResult(currentStep, def.phase(), def.label(), "done", result));
        currentStep++;
        return state();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> reset() {
        return start();
    }

    private void cleanPreviousRun() {
        for (int pr : List.of(101, 102, 103)) {
            try {mergeQueue.dequeue(pr, "casehubio/webapp");} catch (Exception ignored) {}
        }
        var activeStatuses = List.of(WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED, WorkItemStatus.IN_PROGRESS);
        var staleItems = workItemStore.scan(
                                              WorkItemQuery.builder().statusIn(activeStatuses).build())
                                      .stream().filter(wi -> wi.callerRef() != null && wi.callerRef().contains("/pi:")).toList();
        for (var wi : staleItems) {
            try {workItemOperations.cancel(wi.id(), "scenario-reset", "Scenario reset");} catch (Exception ignored) {}
        }
    }


    @GET
    @Path("/outline")
    @Produces(MediaType.APPLICATION_JSON)
    public Object outline() {
        return state().get("outline");
    }

    @POST
    @Path("/pause")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> pause() {
        paused = true;
        return state();
    }

    @POST
    @Path("/resume")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> resume() {
        paused = false;
        return state();
    }


    private Object executeStep(int index) {
        return switch (index) {
            case 0 -> submitPr(101, "abc101abc101", 460, "alice", 1001,
                List.of("src/auth/TokenService.java", "src/auth/SessionManager.java", "src/auth/RBAC.java",
                    "src/config/SecurityConfig.java", "test/auth/TokenServiceTest.java",
                    "test/auth/SessionManagerTest.java", "pom.xml", "docs/security.md"));
            case 1 -> submitPr(102, "def102def102", 50, "bob", 1002,
                List.of("src/service/UserService.java", "src/service/OrderService.java", "src/util/Naming.java"));
            case 2 -> submitPr(103, "ghi103ghi103", 1340, "charlie", 1003,
                List.of("src/payment/PaymentService.java", "src/payment/PaymentGateway.java",
                    "src/payment/StripeAdapter.java", "src/payment/PaymentController.java",
                    "src/order/OrderService.java", "src/order/CheckoutFlow.java",
                    "src/config/PaymentConfig.java", "src/config/ModuleConfig.java", "pom.xml",
                    "payment-module/pom.xml", "payment-module/src/main/java/PaymentApp.java",
                    "test/payment/PaymentServiceTest.java", "test/payment/GatewayTest.java",
                    "test/order/OrderServiceTest.java", "docs/architecture.md", "docs/payment-module.md",
                    "db/migration/V100__payment_tables.sql", "db/migration/V101__payment_indexes.sql",
                    "src/payment/model/Transaction.java", "src/payment/model/Refund.java",
                    "src/payment/spi/PaymentProvider.java", "src/payment/spi/RefundPolicy.java"));
            case 3 -> checkAnalysis();
            case 4 -> approve(102, "reviewer-bot");
            case 5 -> approve(101, "security-reviewer");
            case 6 -> handleHumanGate();
            case 7 -> approve(103, "arch-reviewer");
            case 8 -> signalCiAll();
            case 9 -> enqueueAll();
            case 10 -> trustSummary();
            default -> Map.of("status", "unknown_step");
        };
    }

    private Object submitPr(int prNumber, String sha, int lines, String contributor, long numericId, List<String> paths) {
        var pr     = new PrPayload("casehubio/webapp", prNumber, sha, "main", lines, contributor, numericId, paths);
        var result = reviewService.startReview(pr);
        return Map.of("prNumber", prNumber, "result", result.verdict(), "contributor", contributor, "lines", lines);
    }

    private Object approve(int prNumber, String reviewer) {
        var result = reviewService.signalReviewSubmitted(
            new PrReviewSubmission("casehubio/webapp", prNumber, "approved", System.currentTimeMillis(), reviewer, -1));
        return Map.of("prNumber", prNumber, "result", result.name(), "reviewer", reviewer);
    }

    private Object checkAnalysis() {
        var cases     = caseTracker.activeCases();
        var summaries = new ArrayList<Map<String, Object>>();
        for (var c : cases) {
            summaries.add(Map.of("caseId", c.caseId().toString(), "repo", c.payload().repo(),
                                 "prNumber", c.payload().prNumber(), "status", c.status().name()));
        }
        return Map.of("activeCases", summaries.size(), "cases", summaries);
    }

    private Object handleHumanGate() {
        var pending = workItemStore.scan(
                                           WorkItemQuery.builder().statusIn(List.of(WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED)).build())
                                   .stream().filter(wi -> wi.callerRef() != null && wi.callerRef().contains("/pi:")).toList();
        if (pending.isEmpty()) {
            return Map.of("status", "no_pending_items", "message", "No human-approval WorkItems found");
        }
        var results = new ArrayList<Map<String, Object>>();
        for (var wi : pending) {
            workItemOperations.claim(wi.id(), "lead-dev");
            workItemOperations.start(wi.id(), "lead-dev");
            workItemOperations.complete(wi.id(), "lead-dev", null, "APPROVED");
            results.add(Map.of("workItemId", wi.id().toString(), "title", wi.title(), "outcome", "APPROVED"));
        }
        return Map.of("status", "decided", "items", results);
    }

    private Object signalCiAll() {
        var results = new ArrayList<Map<String, Object>>();
        for (int pr : List.of(101, 102, 103)) {
            var activeCase = caseTracker.findActiveCaseByPr("casehubio/webapp", pr);
            if (activeCase.isPresent()) {
                caseHub.signal(activeCase.get().caseId(), "ci.status", "passing");
                results.add(Map.of("prNumber", pr, "caseId", activeCase.get().caseId().toString(), "status", "passing"));
            }
        }
        return Map.of("signalled", results.size(), "results", results);
    }

    private Object enqueueAll() {
        var      results = new ArrayList<Map<String, Object>>();
        int[][]  prs     = {{102, 0}, {101, 1}, {103, 2}};
        String[] shas    = {"def102def102", "abc101abc101", "ghi103ghi103"};
        String[] authors = {"bob", "alice", "charlie"};
        for (int i = 0; i < prs.length; i++) {
            int pr = prs[i][0];
            try {
                mergeQueue.admit(pr, "casehubio/webapp", shas[i], authors[i]);
                results.add(Map.of("prNumber", pr, "status", "enqueued"));
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) {root = root.getCause();}
                String msg = root.getMessage() != null ? root.getMessage() : root.getClass().getName();
                if (msg.contains("PK_MERGE_QUEUE_BATCH") || msg.contains("primary key violation")) {
                    results.add(Map.of("prNumber", pr, "status", "enqueued", "note", "batch formation deferred"));
                } else {
                    results.add(Map.of("prNumber", pr, "status", "failed", "error", msg));
                }
            }
        }
        return Map.of("results", results);
    }

    private Object trustSummary() {
        return Map.of("status", "trust_snapshot", "message", "Check Trust tab for updated scores");
    }
}
