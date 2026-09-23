# LLM-Powered Reviewer Agents — Design Spec

**Branch:** issue-203-llm-reviewer-agents
**Issue:** casehubio/devtown#203
**Date:** 2026-09-16

## Overview

Augment devtown's existing `ReviewerAgent` stub implementations with LLM-powered code analysis. Both stubs and LLM agents are `@ApplicationScoped`; a `ReviewerAgentRegistry` resolves per-capability by priority, preferring LLM agents over stubs. LLM implementations produce structured `ReviewFinding` records from actual PR diffs via `AgentProvider`.

All 6 ReviewDomain capabilities are covered: code-analysis, security-review, architecture-review, style-review, test-coverage, performance-analysis.

## Architecture

### Dispatch path

LLM reviewers implement the `ReviewerAgent` port interface (`review/`). A new `ReviewerAgentRegistry` indexes agents by `capability()` and resolves per-capability by `priority()` — when both a stub and an LLM agent exist for the same capability, the higher-priority agent wins. The Layer 3 path (QhorusPrReviewService) and Layer 5 engine path both resolve agents through the registry.

**Layer 5 dispatch mechanism:** `PrReviewCaseHub.augment()` registers one function worker per review capability, following the established pattern used by the `merge-executor`. Each function worker receives the engine's narrowed `Map<String, Object>` input (per `inputProjection`), delegates to the registry-resolved `ReviewerAgent`, and returns `WorkerResult<Map<String, Object>>` for the engine to apply via `outputProjection`. This replaces the earlier underspecified `ReviewerAgentCapabilityAdapter` bridge — the adapter logic lives directly in `PrReviewCaseHub` alongside the existing `adaptMerge()` method.

Both stubs and LLM agents are `@ApplicationScoped` — no `@DefaultBean`. The `@DefaultBean` displacement pattern (ARC42STORIES §9.4) is a 1:1 mechanism designed for port interfaces with a single baseline and a single displacer. Applying it to 6 stubs sharing the `ReviewerAgent` bean type would cause any single non-`@DefaultBean` LLM agent to suppress ALL 6 `@DefaultBean` stubs — breaking partial deployment.

Instead, `ReviewerAgentRegistry` handles per-capability agent selection with explicit priority ordering. `ReviewerAgent` gains a `default int priority()` method (returns 0). `LlmReviewerAgent` overrides with `priority()` returning 1. When multiple agents share a capability, the registry selects the highest-priority agent. This supports incremental deployment: deploying 2 LLM agents means those 2 capabilities use LLM agents (priority 1) while the other 4 continue using stubs (priority 0).

### Module placement

| Component | Module | Rationale |
|-----------|--------|-----------|
| `ReviewFinding`, `ReviewFinding.Severity`, `ReviewFinding.LineRange` | `domain/` | Pure Java domain vocabulary, no framework deps |
| `ReviewContext` | `review/` | Bundles `PrPayload` + `PrDiff` for agent dispatch |
| `ReviewFindings` (JSON wrapper) | `review/` | Deserialization target for `StructuredAgentInvoker`; only used by `LlmReviewerAgent` |
| `PrDiffService`, `PrDiff`, `PrDiff.FileDiff` | `review/` | Port interface alongside ReviewerAgent |
| `CodeAnalysisAgent` (port interface) | `review/` | Separate port for PR classification; returns `CodeAnalysisResult`, not findings |
| `LlmAgentBase` (abstract base) | `review/` | Shared retry, timeout, model resolution — does NOT implement any port interface |
| `LlmReviewerAgent` (abstract, extends `LlmAgentBase` implements `ReviewerAgent`) | `review/` | Adds batching, findings extraction, file path validation |
| `ReviewerAgentRegistry` | `review/` | Port-level coordination with priority-based resolution |
| `PrDiffCache` | `review/` | Application-scoped cache wrapping `PrDiffService`, keyed by `(repo, prNumber, headSha)` |
| `GitHubPrDiffClient` | `github/` | GitHub REST implementation of PrDiffService |
| `LlmSecurityReviewAgent`, etc. (5 concrete agents) | `app/agents/` | CDI wiring, AgentProvider injection; extend `LlmReviewerAgent` |
| `LlmCodeAnalysisAgent` (extends `LlmAgentBase` implements `CodeAnalysisAgent`) | `app/agents/` | Classification agent with `CodeAnalysisResult` deserialization |
| Stub agents (4 existing + 2 new, all `@ApplicationScoped`) | `app/agents/` | No @DefaultBean — priority-based displacement via registry |
| Review adapter (in `PrReviewCaseHub.augment()`) | `app/` | Function workers bridging engine capability dispatch to `ReviewerAgentRegistry` |
| Eidos descriptors | `app/src/main/resources/META-INF/eidos/descriptors.yaml` | Agent identity declarations |
| Agent config manifest | `agent-config.yaml` (project root) | Model aliases and provider credentials |

## Domain Model Changes

### ReviewFinding (new)

```java
public record ReviewFinding(
    Severity severity,
    String category,
    String filePath,
    LineRange lineRange,    // nullable — some findings are file-level
    String message,
    double confidence       // 0.0–1.0, clamped
) {
    public ReviewFinding {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
    public record LineRange(int startLine, int endLine) {}
}
```

Compact constructor clamps `confidence` to [0.0, 1.0]. LLMs may return percentage values (e.g. 95 instead of 0.95) — clamping ensures trust calibration tracking receives valid data rather than silently corrupting calibration models.

### ReviewerOutcome change

`Completed` changes from `List<String>` to `List<ReviewFinding>`. Breaking change — pre-release, all callers updated.

#### Ripple analysis

| File | Current type | Change required |
|------|-------------|-----------------|
| `ReviewerOutcome.Completed` | `List<String> findings` | → `List<ReviewFinding> findings` |
| `PrReviewOutcome` | `List<String> findings` | → `List<ReviewFinding> findings` |
| `QhorusPrReviewService` line 78 | `List<String> allFindings` | → `List<ReviewFinding> allFindings` |
| `QhorusPrReviewService` line 97 | `String.join("; ", completed.findings())` | Format ReviewFinding list for qhorus message |
| `QhorusPrReviewService` line 106 | `allFindings.addAll(completed.findings())` | Type-compatible with new `List<ReviewFinding>` |
| `PrReviewCaseService` lines 86, 160 | `List.of()` as `List<String>` | `List.of()` as `List<ReviewFinding>` (inferred, no code change) |

### ReviewFindings (JSON wrapper)

```java
public record ReviewFindings(List<ReviewFinding> findings) {
    public ReviewFindings {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
```

Deserialization target for `StructuredAgentInvoker.invoke()`. Compact constructor normalises null to empty list — LLMs may return `{}` or `{"findings": null}` instead of `{"findings": []}`. `List.copyOf` ensures immutability.

### Trust scoring mapping

Each `ReviewFinding` becomes a data point for trust attestation via the existing per-case pipeline — no changes to trust infrastructure required.

**Integration path:** When a review case completes, the existing `IncrementalTrustUpdateObserver` fires on `AttestationRecordedEvent`. The attestation records whether the case was SOUND or FLAGGED. `ReviewFinding` data enriches the attestation metadata: finding count, severity distribution, and confidence scores are attached to the case-level attestation.

**Confirmation flow:** The human gate reviewer (already defined in `pr-review.yaml` as `human-approval` binding) sees findings as part of the review. Their APPROVED/REJECTED/BLOCKED decision on the case implicitly confirms or refutes the LLM findings:
- Case APPROVED with LLM findings → findings treated as true positives (developer addressed them) → improves `review-thoroughness`
- Case FLAGGED for false positives → worsens `false-positive-rate`
- `confidence` field enables calibration tracking — over many reviews, observed true-positive rates can be compared against stated confidence to detect systematic over/under-confidence

**Granularity:** Per-case, not per-finding. This is intentional — the existing trust pipeline operates at case-completion granularity via `LedgerAttestation` records. Per-finding attestation would require extending the trust infrastructure, which is out of scope for this issue.

## Review Port Changes

### ReviewContext (new, review/)

```java
public record ReviewContext(PrPayload pr, PrDiff diff) {}
```

Bundles PR metadata with the fetched diff. The dispatcher (Layer 3 loop or Layer 5 capability adapter) fetches the diff once and passes it to all agents via `ReviewContext`. This eliminates redundant diff fetching — without it, each of 6 agents would independently call `PrDiffService.fetchDiff()` for the same PR.

### ReviewerAgent interface changes

```java
public interface ReviewerAgent {
    String capability();
    ReviewerOutcome handle(ReviewContext context);
    default int priority() { return 0; }
}
```

Two changes from the current interface:
1. **`handle(PrPayload pr)` → `handle(ReviewContext context)`** — agents receive the diff as input rather than fetching it. Breaking change; all implementations updated.
2. **`default int priority()`** — enables per-capability displacement via the registry. Stubs return 0 (default). `LlmReviewerAgent` overrides with 1. The registry selects the highest-priority agent per capability.

`ReviewerAgent` is for findings-based review capabilities only (security-review, architecture-review, style-review, test-coverage, performance-analysis). Code-analysis uses its own port interface — see below.

### CodeAnalysisAgent interface (new, review/)

```java
public interface CodeAnalysisAgent {
    CodeAnalysisResult analyse(ReviewContext context);
    default int priority() { return 0; }
}
```

Separate port interface for PR classification. Returns `CodeAnalysisResult` (classification fields: `complete`, `securitySensitive`, `architectureCrossing`, `scope`, `flaggedFiles`, `crossingPoints`) rather than `ReviewerOutcome`. This separation exists because:

1. **Different output type:** Classification fields drive downstream binding conditions (`.codeAnalysis.securitySensitive == true`). Findings-based capabilities produce verdict+findings. The sealed `ReviewerOutcome` type has no variant for classification data, and adding one would force review-specific types to know about classification.
2. **Different invocation pattern:** Code-analysis is a single LLM invocation per PR. Findings-based capabilities batch files.
3. **Different role in the pipeline:** Code-analysis drives routing (which capabilities fire). Review capabilities produce the review itself.

Priority-based displacement: `CodeAnalysisAgentStub` returns priority 0, `LlmCodeAnalysisAgent` returns priority 1. `PrReviewCaseHub` selects the highest-priority implementation from `Instance<CodeAnalysisAgent>`.

## PrDiffService SPI

### Interface (review/)

```java
public interface PrDiffService {
    PrDiff fetchDiff(String repo, int prNumber);
}
```

### PrDiff model (review/)

```java
public record PrDiff(
    String repo,
    int prNumber,
    String baseSha,
    String headSha,
    List<FileDiff> files,
    boolean truncated          // true when GitHub's 3000-file cap was hit
) {
    public record FileDiff(
        String path,
        String status,       // "added", "modified", "removed", "renamed"
        String patch,        // nullable — null for binary files, large diffs, renames with no content change
        int additions,
        int deletions
    ) {
        public boolean hasReviewablePatch() {
            return patch != null && !patch.isBlank();
        }
    }
}
```

`patch` is explicitly nullable. GitHub returns null for binary files, diffs exceeding ~1MB, and renames without content changes. `hasReviewablePatch()` filters non-reviewable files before batching — these files are logged as "unanalyzable" in the review output but do not cause failures. `truncated` signals when the PR exceeds GitHub's 3000-file API cap.

### GitHubPrDiffClient (github/)

Implements `PrDiffService` using GitHub REST API `GET /repos/{owner}/{repo}/pulls/{pull_number}/files`. Returns per-file patches — GitHub's API returns file-level diffs naturally.

#### Pagination

GitHub paginates this endpoint (max 100 per page with `per_page=100`, default 30). `GitHubPrDiffClient` follows `Link` header pagination to collect all pages. GitHub imposes a hard cap of 3000 files — PRs exceeding this return the first 3000 only.

When the 3000-file cap is hit, `PrDiff.truncated` is set to `true`. The review proceeds on the available files and the `ReviewerOutcome` logs the truncation. This is a data-integrity boundary: the review is explicitly partial, and trust scoring records it as such.

#### Rate limiting

GitHub API rate limiting is handled by the existing `@RegisterRestClient` infrastructure. `GitHubPrDiffClient` shares the application-level rate limit handler with all other GitHub REST clients — no additional rate limiting logic is needed. Concurrent PR reviews are bounded by the engine's `maxConcurrentDispatches: 5` setting in `pr-review.yaml`, which limits how many capabilities can be dispatched simultaneously.

#### Null patches

`FileDiff.patch` is null for binary files, diffs exceeding GitHub's ~1MB size limit, and renames with no content changes. `LlmReviewerAgent.batchFiles()` filters these using `hasReviewablePatch()` and logs them as unanalyzable.

## Agent Identity and Model Selection

### Eidos integration

Each reviewer agent is registered as an eidos `AgentDescriptor` via `META-INF/eidos/descriptors.yaml`. Dependencies added: `casehub-eidos-api` (compile), `casehub-eidos` (runtime).

`epistemicDomains` is included only for `security-reviewer`. This is intentional: security analysis is where language-specific confidence varies most (injection patterns, crypto APIs, and deserialization risks differ substantially across Java, Python, and TypeScript). Other capabilities (code-analysis, style, test-coverage, performance, architecture) operate on structural and pattern-level concerns that are more language-agnostic. The field is optional on `AgentCapability` — omission means uniform confidence across languages.

```yaml
- agentId: security-reviewer
  name: Security Review Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: security-review
      description: "OWASP top 10, auth, injection, secrets exposure"
      modelRef: reasoning-heavy
      qualityHint: 0.85
      epistemicDomains:
        java: 0.9
        python: 0.8
        typescript: 0.7

- agentId: code-analyzer
  name: Code Analysis Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: code-analysis
      description: "Code characterisation: scope, security sensitivity, architecture crossing"
      modelRef: standard-review
      qualityHint: 0.8

- agentId: architecture-reviewer
  name: Architecture Review Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: architecture-review
      description: "Structural impact, coupling, API surface changes"
      modelRef: reasoning-heavy
      qualityHint: 0.8

- agentId: style-reviewer
  name: Style Review Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: style-review
      description: "Naming, consistency, idiomatic patterns"
      modelRef: cheap-fast
      qualityHint: 0.7

- agentId: test-coverage-reviewer
  name: Test Coverage Review Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: test-coverage
      description: "Untested paths, missing edge cases"
      modelRef: standard-review
      qualityHint: 0.75

- agentId: performance-reviewer
  name: Performance Review Agent
  slot: reviewer
  tenancyId: default
  capabilities:
    - name: performance-analysis
      description: "N+1 queries, unbounded loops, memory issues"
      modelRef: standard-review
      qualityHint: 0.75
```

### Agent config manifest (project root)

```yaml
# agent-config.yaml
providers:
  - vendor: anthropic
    credential: env:ANTHROPIC_API_KEY
aliases:
  reasoning-heavy:
    tier: FLAGSHIP
    capabilities: [reasoning]
  standard-review:
    tier: STANDARD
  cheap-fast:
    tier: FAST
    max-cost: LOW
defaults:
  backend: claude
```

Model resolution: `modelRef: "reasoning-heavy"` → manifest alias → tier/vendor/model per environment.

## LLM ReviewerAgent Implementation

### LlmAgentBase — shared infrastructure (review/)

Uses `StructuredAgentInvoker` from `casehub-blocks-core` (transitive dependency via `casehub-blocks`, already present in `review/` and `app/`). Provides retry, timeout, and model resolution for all LLM-based agents — both findings-based reviewers and the code-analysis classifier.

```java
public abstract class LlmAgentBase {

    protected static final int MAX_RETRIES = 2;
    protected static final Duration INVOCATION_TIMEOUT = Duration.ofMinutes(3);

    protected abstract AgentProvider agentProvider();
    protected abstract AgentRegistry agentRegistry();

    protected abstract String agentId();
    protected abstract String systemPromptBody();

    public abstract String capability();

    protected String resolveModelRef() { /* see below */ }

    protected <T> InvocationResult<T> invokeWithRetry(
            AgentSessionConfig config, int maxRetries, Class<T> responseType) { /* see below */ }
}
```

`LlmAgentBase` does NOT implement any port interface — it is pure infrastructure. Subclasses implement the appropriate port interface and define their own invocation strategy:
- `LlmReviewerAgent implements ReviewerAgent` (for findings-based capabilities): batching, findings extraction, file path validation
- `LlmCodeAnalysisAgent implements CodeAnalysisAgent` (for classification): single invocation, `CodeAnalysisResult` deserialization

### LlmReviewerAgent — batched findings (review/, extends LlmAgentBase implements ReviewerAgent)

Extends `LlmAgentBase` and implements `ReviewerAgent`. Adds batching and findings extraction logic.

```java
public abstract class LlmReviewerAgent extends LlmAgentBase {

    private static final int BATCH_TOKEN_BUDGET = 8_000;
    private static final int MAX_BATCHES = 10;

    @Override
    public int priority() { return 1; }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        PrDiff diff = context.diff();
        List<FileDiff> reviewable = diff.files().stream()
            .filter(FileDiff::hasReviewablePatch)
            .toList();

        if (reviewable.isEmpty()) {
            return new ReviewerOutcome.Declined("no reviewable files");
        }

        String modelRef = resolveModelRef();
        List<ReviewFinding> allFindings = new ArrayList<>();
        List<String> batchErrors = new ArrayList<>();
        boolean anyBatchSucceeded = false;
        Set<String> diffPaths = reviewable.stream()
            .map(FileDiff::path).collect(Collectors.toSet());

        var batches = batchFiles(reviewable);
        if (batches.size() > MAX_BATCHES) {
            log.warnf("PR has %d batches, capping at %d — %d files skipped",
                batches.size(), MAX_BATCHES,
                batches.subList(MAX_BATCHES, batches.size()).stream()
                    .mapToInt(List::size).sum());
            batches = batches.subList(0, MAX_BATCHES);
        }

        for (var batch : batches) {
            var config = AgentSessionConfig.of(
                buildSystemPrompt(), buildUserPrompt(context.pr(), batch))
                .withModel(modelRef)
                .withTimeout(INVOCATION_TIMEOUT);

            var result = invokeWithRetry(config, MAX_RETRIES);

            switch (result) {
                case InvocationResult.Success<ReviewFindings> s -> {
                    anyBatchSucceeded = true;
                    var validated = s.value().findings().stream()
                        .filter(f -> diffPaths.contains(f.filePath()))
                        .toList();
                    allFindings.addAll(validated);
                }
                case InvocationResult.ParseError<ReviewFindings> pe ->
                    batchErrors.add("batch parse error: " + pe.parseError());
                case InvocationResult.AgentError<ReviewFindings> ae ->
                    batchErrors.add("batch agent error: " + ae.reason());
            }
        }

        if (!anyBatchSucceeded && !batchErrors.isEmpty()) {
            return new ReviewerOutcome.Failed(
                "all batches failed: " + String.join("; ", batchErrors));
        }

        return new ReviewerOutcome.Completed(allFindings);
    }

    // invokeWithRetry() and resolveModelRef() are inherited from LlmAgentBase

    protected List<List<FileDiff>> batchFiles(List<FileDiff> files) {
        // Filters: only files passing hasReviewablePatch() (already done by caller)
        // Estimation: characters / 4 as token proxy (conservative for code)
        // Budget: BATCH_TOKEN_BUDGET tokens per batch (~8K), excluding prompt overhead
        // Overflow: single files exceeding budget get their own batch
        // Context cap: files whose patch alone exceeds model context window
        //   are truncated to the first BATCH_TOKEN_BUDGET * 4 characters with
        //   a "[truncated]" marker — partial review > no review
    }

    private String buildSystemPrompt() {
        return systemPromptBody() + "\n\n" + RESPONSE_SCHEMA;
    }

    private String buildUserPrompt(PrPayload pr, List<FileDiff> batch) {
        // PR metadata + unified diff content for the batch
    }

    private static final String RESPONSE_SCHEMA = """
        Respond with a JSON object matching this schema exactly:
        {"findings": [{"severity": "CRITICAL|HIGH|MEDIUM|LOW|INFO",
          "category": "<string>", "filePath": "<string>",
          "lineRange": {"startLine": <int>, "endLine": <int>} | null,
          "message": "<string>", "confidence": <0.0-1.0>}]}
        If no findings, respond with: {"findings": []}""";
}
```

#### Token budget and cost control

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `BATCH_TOKEN_BUDGET` | 8,000 tokens | Target input tokens per batch (chars/4 estimation) |
| `MAX_BATCHES` | 10 | Max LLM invocations per agent per review |
| `MAX_RETRIES` | 2 | Max retry attempts for transient failures per batch |
| `INVOCATION_TIMEOUT` | 3 minutes | Per-invocation timeout |

**Budget exceeded:** When `MAX_BATCHES` is reached, remaining files are skipped with a warning log. Partial review (10 batches ≈ 80K tokens of diff) is more useful than a failed review. The 10-batch cap bounds cost at roughly 10 × (8K input + response output) tokens per capability per review.

**Token counting:** Character-based approximation (characters / 4). This is intentionally conservative — overestimating tokens means fewer files per batch, not truncation. Exact tokenizer integration is deferred to implementation; the approximation is sufficient for batching decisions.

**Per-review cost visibility:** With `maxConcurrentDispatches: 5`, up to 5 review capabilities can fire concurrently, each invoking up to 10 batches with up to 2 retries. Worst case: 5 × 10 × 3 = 150 LLM invocations per PR review. `InvocationMetadata.totalCostUsd` (returned by `StructuredAgentInvoker`) is logged per-invocation. The function worker adapter aggregates and logs total cost per capability invocation. A per-review budget cap is deferred — it would require cross-worker coordination that the engine's stateless function worker dispatch doesn't natively support.

#### Batch resilience

Failed batches do not discard findings from successful batches. Each batch is processed independently — errors are collected, not propagated. If at least one batch succeeded, findings from successful batches are returned via `Completed`. Only when ALL batches fail does the method return `Failed`.

#### Retry logic (inherited from LlmAgentBase)

`invokeWithRetry` retries `AgentError` results (transient API failures: timeouts, rate limits, network errors) up to `MAX_RETRIES` times. `ParseError` results are NOT retried — the LLM produced structurally invalid output, which a retry is unlikely to fix. This per-invocation retry operates below the engine's failure cascade: the cascade reroutes to different agents; retry handles transient failures within the same agent.

Both `LlmReviewerAgent` (batched findings) and `LlmCodeAnalysisAgent` (single classification) inherit this retry logic from `LlmAgentBase`. `LlmCodeAnalysisAgent` calls `invokeWithRetry()` once per review; `LlmReviewerAgent` calls it once per batch.

#### Timeout (inherited from LlmAgentBase)

`AgentSessionConfig.withTimeout(INVOCATION_TIMEOUT)` sets a 3-minute timeout per LLM invocation. Enforcement depends on the `AgentProvider` implementation — `casehub-blocks` providers honour the config timeout via reactive stream subscription timeouts. `StructuredAgentInvoker.invoke()` catches the resulting `TimeoutException` and returns `AgentError`.

#### File path validation

Findings with `filePath` values not present in the actual diff are filtered out after deserialization. LLMs may hallucinate file paths that exist in the repo but weren't changed in the PR — these would corrupt trust scoring if recorded as false positives.

### Concrete agents (app/agents/)

Each is thin — system prompt + capability tag + CDI wiring:

```java
@ApplicationScoped
public class LlmSecurityReviewAgent extends LlmReviewerAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.SECURITY_REVIEW; }
    @Override protected String agentId() { return "security-reviewer"; }

    @Override
    protected String systemPromptBody() {
        return """
            You are a security code reviewer. Analyze the code diff for:
            - OWASP top 10 vulnerabilities
            - Authentication and authorization issues
            - Injection vulnerabilities (SQL, command, XSS)
            - Secrets or credentials in code
            - Cryptographic misuse
            - Insecure deserialization
            Rate each finding by severity and your confidence level.""";
    }
}
```

### Error handling

| Failure | Handler | Outcome |
|---------|---------|---------|
| `AgentProvider` timeout/process error | `invokeWithRetry` retries up to MAX_RETRIES, then batch error collected | `Failed` only if ALL batches fail |
| Invalid JSON from LLM | `InvocationResult.ParseError` — NOT retried (structural, not transient) | Batch error collected, other batches continue |
| Single batch failure (partial) | Error logged, successful batch findings retained | `Completed` with partial findings |
| All batches fail | No findings to return | `Failed(aggregated error message)` → failure cascade |
| Empty/no-reviewable diff | Detected before LLM invocation | `Declined("no reviewable files")` |
| `PrDiffService` failure | Exception at dispatch level (Layer 3 try-catch or Layer 5 adapter) | Agent not invoked; FAILED propagated per dispatch path |
| `PrDiffService` 403 rate-limit | Same as above — diff fetch is a single point before the agent loop | Other agents also skip (all share the same diff fetch) |
| LLM provider hang (no data) | `AgentSessionConfig.withTimeout(3min)` → provider-level stream timeout → `AgentError` | Retried via `invokeWithRetry`, then batch error collected |
| Hallucinated file paths | Post-deserialization validation against diff file set | Finding silently filtered out |
| Confidence outside [0,1] | `ReviewFinding` compact constructor clamps | Value normalised, no error |
| `findings: null` in JSON | `ReviewFindings` compact constructor normalises to empty list | No NPE |
| PR exceeds 3000-file GitHub cap | `PrDiff.truncated = true`, review proceeds on available files | Partial review, logged for trust scoring |
| Max batches exceeded | Files beyond `MAX_BATCHES` (10) batches skipped with warning log | Partial review — 10 batches ≈ 80K tokens of diff coverage |

## ReviewerAgentRegistry

```java
@ApplicationScoped
public class ReviewerAgentRegistry {

    private final Map<String, ReviewerAgent> agentsByCapability;

    @Inject
    public ReviewerAgentRegistry(Instance<ReviewerAgent> agents) {
        agentsByCapability = new HashMap<>();
        for (ReviewerAgent agent : agents) {
            agentsByCapability.merge(agent.capability(), agent,
                (existing, incoming) ->
                    incoming.priority() > existing.priority() ? incoming : existing);
        }
    }

    public Optional<ReviewerAgent> forCapability(String capability) {
        return Optional.ofNullable(agentsByCapability.get(capability));
    }

    public Collection<ReviewerAgent> all() {
        return agentsByCapability.values();
    }
}
```

The constructor uses `merge()` with priority comparison: when two agents declare the same capability, the higher-priority agent wins. This enables incremental LLM deployment — deploying `LlmSecurityReviewAgent` (priority 1) displaces only `SecurityReviewAgent` (priority 0), leaving the other 5 stubs active.

### Stub agents

#### ReviewerAgent stubs

| Agent | Capability | Status | Change |
|-------|-----------|--------|--------|
| SecurityReviewAgent | security-review | Existing | Update `handle()` signature to `ReviewContext` |
| ArchitectureReviewAgent | architecture-review | Existing | Update `handle()` signature to `ReviewContext` |
| TestCoverageReviewAgent | test-coverage | Existing | Update `handle()` signature to `ReviewContext` |
| PerformanceAnalysisAgent | performance-analysis | Existing | Update `handle()` signature to `ReviewContext` |
| **StyleReviewAgent** (new) | style-review | New stub | Create as `@ApplicationScoped`, priority 0 |

All `ReviewerAgent` stubs remain `@ApplicationScoped` — no `@DefaultBean`. The registry's priority-based resolution handles displacement.

#### CodeAnalysisAgent stub

| Agent | Port interface | Status | Change |
|-------|---------------|--------|--------|
| **CodeAnalysisAgentStub** (new) | `CodeAnalysisAgent` | New stub | `@ApplicationScoped`, priority 0; returns default `CodeAnalysisResult(true, false, false, "unknown", List.of(), List.of())` |

`CodeAnalysisAgent` is a separate port interface — code-analysis is classification, not review (see §Code-analysis differentiation). `PrReviewCaseHub` selects the highest-priority `CodeAnalysisAgent` from `Instance<CodeAnalysisAgent>` during `augment()`.

### Review capability function workers (in PrReviewCaseHub)

`PrReviewCaseHub.augment()` registers one function worker per review capability, following the established `merge-executor` pattern:

```java
@Inject ReviewerAgentRegistry registry;
@Inject Instance<CodeAnalysisAgent> codeAnalysisAgents;
@Inject PrDiffCache diffCache;

@Override
protected void augment(CaseDefinition definition) {
    definition.getWorkers().add(Worker.builder()
        .name("merge-executor")
        .capabilityName("merge-executor")
        .function(this::adaptMerge)
        .build());

    // Code-analysis — separate port interface, separate adapter
    definition.getWorkers().add(Worker.builder()
        .name("code-analyzer")
        .capabilityName(ReviewDomain.CODE_ANALYSIS)
        .function(this::adaptCodeAnalysis)
        .build());

    // Findings-based review capabilities — through ReviewerAgentRegistry
    for (String capability : ReviewDomain.FINDINGS_CAPABILITIES) {
        definition.getWorkers().add(Worker.builder()
            .name("reviewer-" + capability)
            .capabilityName(capability)
            .function(input -> adaptReview(capability, input))
            .build());
    }
}
```

`ReviewDomain.FINDINGS_CAPABILITIES` is a new constant — the 5 review capabilities excluding `CODE_ANALYSIS` (security-review, architecture-review, style-review, test-coverage, performance-analysis). Code-analysis is registered with its own function worker because it has a fundamentally different output shape (classification fields, not verdict+findings).

The `adaptReview()` method handles findings-based review capabilities:

```java
WorkerResult adaptReview(String capability, Map<String, Object> input) {
    try {
        var agent = registry.forCapability(capability);
        if (agent.isEmpty()) {
            return WorkerResult.failed("no agent registered for " + capability);
        }

        ReviewContext context = buildContext(input);

        return switch (agent.get().handle(context)) {
            case ReviewerOutcome.Completed c -> {
                String verdict = c.findings().stream()
                    .anyMatch(f -> f.severity() == CRITICAL || f.severity() == HIGH)
                    ? "REJECTED" : "APPROVED";
                yield WorkerResult.of(Map.of(
                    "outcome", verdict,
                    "findings", serializeFindings(c.findings())));
            }
            case ReviewerOutcome.Declined d ->
                WorkerResult.of(Map.of("outcome", "APPROVED"));
            case ReviewerOutcome.Failed f ->
                WorkerResult.failed(f.reason());
        };
    } catch (Exception e) {
        return WorkerResult.failed(capability + " adapter error: " + e.getMessage());
    }
}
```

The `adaptCodeAnalysis()` method handles the code-analysis capability separately:

```java
WorkerResult adaptCodeAnalysis(Map<String, Object> input) {
    try {
        CodeAnalysisAgent agent = codeAnalysisAgents.stream()
            .max(Comparator.comparingInt(CodeAnalysisAgent::priority))
            .orElseThrow(() -> new IllegalStateException("no CodeAnalysisAgent registered"));

        ReviewContext context = buildContext(input);
        CodeAnalysisResult result = agent.analyse(context);

        return WorkerResult.of(Map.of(
            "complete", result.complete(),
            "securitySensitive", result.securitySensitive(),
            "architectureCrossing", result.architectureCrossing(),
            "scope", result.scope(),
            "flaggedFiles", result.flaggedFiles(),
            "crossingPoints", result.crossingPoints()));
    } catch (Exception e) {
        return WorkerResult.failed("code-analysis adapter error: " + e.getMessage());
    }
}

private ReviewContext buildContext(Map<String, Object> input) {
    Map<String, Object> prMap = (Map<String, Object>) input.get("pr");
    String repo = (String) prMap.get("repo");
    int prNumber = Integer.parseInt((String) prMap.get("id"));
    String headSha = (String) prMap.get("headSha");

    PrDiff diff = diffCache.get(repo, prNumber, headSha);
    PrPayload pr = PrPayload.fromContextMap(prMap);
    return new ReviewContext(pr, diff);
}
```

**Why code-analysis has its own adapter:**  Code-analysis produces `CodeAnalysisResult` (classification fields: `complete`, `securitySensitive`, `architectureCrossing`), not `ReviewerOutcome.Completed(List<ReviewFinding>)`. The sealed `ReviewerOutcome` type cannot carry classification data — and it shouldn't, because classification is fundamentally different from review. Downstream bindings depend on these specific classification fields (`.codeAnalysis.complete == true`, `.codeAnalysis.securitySensitive == true`) for routing. The `adaptCodeAnalysis()` method serialises `CodeAnalysisResult` fields directly into the `WorkerResult` output Map, producing the correct context shape for these binding conditions.

**Why `WorkerResult.of()` for Declined, not `WorkerResult.declined()`:** The pr-review.yaml outcomePolicy specifies `onDecline: REROUTE`. Using `WorkerResult.declined()` would trigger the engine's reroute loop, attempting to provision another worker for the capability. For "no reviewable files", the correct semantics is "no objection" — return APPROVED via `WorkerResult.of()` so the engine writes the outcome to context and the case proceeds.

**Exception handling:** Both adapter methods wrap their logic in try-catch, returning `WorkerResult.failed()` on any unexpected exception. The existing `adaptMerge()` lets exceptions propagate to the engine, but LLM-powered agents have more failure modes (API errors, JSON parsing, ClassCastException from Map extraction) that should be handled gracefully at the adapter level rather than faulting the engine.

**Why no separate `ReviewResult` type:** The function worker returns `WorkerResult<Map<String, Object>>` directly. The `WorkerResult` outcome semantics (`Success`, `Declined`, `Failed`, `Expired`) already model the agent's result disposition. The engine's `OutcomePolicy` handles routing. Adding an intermediate `ReviewResult` type would duplicate `WorkerResult` semantics without adding value.

#### PrPayload reconstruction from engine context

The engine's `inputProjection` delivers a narrowed `Map<String, Object>` to each function worker. The `.pr` field is a Map constructed by `PrReviewCaseService.startReview()`:

| Context Map key | Type | PrPayload field | Notes |
|----------------|------|-----------------|-------|
| `id` | `String` | `prNumber` (int) | `Integer.parseInt()` |
| `repo` | `String` | `repo` | Direct |
| `linesChanged` | `int` | `linesChanged` | Direct |
| `baseRef` | `String` | `baseRef` | Direct |
| `headSha` | `String` | `headSha` | Direct |
| `contributor` | `String` | `contributor` | Direct |
| `changedPaths` | `List<String>` | `changedPaths` | Direct |
| — | — | `contributorNumericId` | Not in context Map; set to `-1` |

`PrPayload.fromContextMap(Map<String, Object>)` centralises this reconstruction with explicit type coercion. The `contributorNumericId` field is not stored in the case context (intentional — it's only needed for GitHub API calls at webhook ingestion time, not during review). Review agents do not use it.

#### Diff caching across capability dispatches

The engine dispatches up to 5 capabilities concurrently (`maxConcurrentDispatches: 5`). Each function worker invocation is stateless — no shared state between capability dispatches. Diff caching uses an `@ApplicationScoped` cache:

```java
@ApplicationScoped
public class PrDiffCache {
    @Inject PrDiffService diffService;

    private final Cache<CacheKey, PrDiff> cache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    public PrDiff get(String repo, int prNumber, String headSha) {
        return cache.get(new CacheKey(repo, prNumber, headSha),
            k -> diffService.fetchDiff(k.repo(), k.prNumber()));
    }

    record CacheKey(String repo, int prNumber, String headSha) {}
}
```

Cache key includes `headSha` — when `revisePr()` pushes a new headSha, the old diff is naturally stale (different key). Caffeine's `Cache.get()` is thread-safe: concurrent capability dispatches for the same PR share a single diff fetch.

#### Output projection updates

The pr-review.yaml output projections must be updated for the 5 review capabilities to accommodate the structured output:

| Capability | Current outputProjection | Updated outputProjection |
|-----------|------------------------|-------------------------|
| code-analysis | `{ codeAnalysis: . }` | No change |
| security-review | `{ securityReview: { outcome: . } }` | `{ securityReview: . }` |
| architecture-review | `{ architectureReview: { outcome: . } }` | `{ architectureReview: . }` |
| style-review | `{ styleCheck: { outcome: . } }` | `{ styleCheck: . }` |
| test-coverage | `{ testCoverage: { outcome: . } }` | `{ testCoverage: . }` |
| performance-analysis | `{ performanceAnalysis: { outcome: . } }` | `{ performanceAnalysis: . }` |

With the updated projections, `WorkerResult.of(Map.of("outcome", "APPROVED", "findings", [...]))` produces context state `{ securityReview: { outcome: "APPROVED", findings: [...] } }`. The existing binding conditions (`.securityReview.outcome == "APPROVED"`) work unchanged, and findings become available in the case context for trust scoring.

#### Code-analysis differentiation

The code-analysis capability classifies the PR rather than producing findings. Its output drives downstream routing (which review capabilities fire). `LlmCodeAnalysisAgent` uses `CodeAnalysisResult` as its deserialization target:

```java
public record CodeAnalysisResult(
    boolean complete,
    boolean securitySensitive,
    boolean architectureCrossing,
    String scope,
    List<String> flaggedFiles,
    List<String> crossingPoints
) {
    public CodeAnalysisResult {
        complete = true;
        flaggedFiles = flaggedFiles == null ? List.of() : List.copyOf(flaggedFiles);
        crossingPoints = crossingPoints == null ? List.of() : List.copyOf(crossingPoints);
    }
}
```

The code-analysis output projection `{ codeAnalysis: . }` is unchanged — the `adaptCodeAnalysis()` function worker serialises `CodeAnalysisResult` fields directly into the `WorkerResult` output Map. The binding conditions (`.codeAnalysis.complete == true`, `.codeAnalysis.securitySensitive == true`) match the record field names.

`LlmCodeAnalysisAgent` extends `LlmAgentBase` and implements `CodeAnalysisAgent` (not `ReviewerAgent`). It shares retry, timeout, and model resolution logic but has its own `analyse()` implementation: single LLM invocation (no batching), `CodeAnalysisResult` deserialization. The `adaptCodeAnalysis()` function worker in `PrReviewCaseHub` handles the adapter return path, serialising `CodeAnalysisResult` fields into the output Map (see §Review capability function workers).

#### Layer 3 dispatch changes

`QhorusPrReviewService` (Layer 3, inactive when Layer 5 is deployed) changes from `Instance<ReviewerAgent>` iteration to `ReviewerAgentRegistry` lookup. The diff is fetched once and shared:

```java
PrDiff diff = diffService.fetchDiff(pr.repo(), pr.prNumber());
ReviewContext context = new ReviewContext(pr, diff);

for (ReviewerAgent agent : registry.all()) {
    try {
        ReviewerOutcome outcome = agent.handle(context);
        // handle Completed, Declined, Failed — dispatch qhorus messages
    } catch (Exception e) {
        messageService.dispatch(/* FAILED message with e.getMessage() */);
    }
}
```

Each agent invocation is wrapped in try-catch — an exception in one agent does not abort the dispatch loop. The diff is fetched once before the loop, eliminating redundant API calls.

## Testing Strategy

### Dependencies

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-simulation-starter</artifactId>
  <scope>test</scope>
</dependency>
```

### Unit tests — Simulation.forTest() fluent API

```java
class LlmSecurityReviewAgentTest {

    @Test
    void producesStructuredFindings() {
        var expectedJson = """
            {"findings": [{"severity": "HIGH", "category": "injection",
             "filePath": "src/Api.java",
             "lineRange": {"startLine": 42, "endLine": 45},
             "message": "SQL injection via string concat",
             "confidence": 0.92}]}""";

        var sim = Simulation.forTest()
            .stub("agent-provider.invoke",
                new AgentSimulationInput("You are a security reviewer...", "...", null),
                List.of(new AgentEvent.TextDelta(expectedJson),
                        new AgentEvent.InvocationComplete(
                            500, 200, 0, 0, 0, null, 1200L, 800L, null, 1, false)))
            .build();

        // invoke agent with sim's AgentProvider, assert findings
        sim.verifier().method("agent-provider.invoke").wasCalled(1);
    }
}
```

### Integration tests — simulation.yaml

```yaml
# src/test/resources/simulation.yaml
profiles:
  review-test:
    strategies:
      agent-provider.invoke: key
    corpus:
      - fixtures/llm-review-corpus.yaml

  github-test:
    strategies:
      github-pull-request-api.listPullRequests: key
    corpus:
      - fixtures/github-api-corpus.yaml

default-profile: review-test
```

### GitHub API simulation

The REST client simulation generator produces decorators for `@RegisterRestClient` interfaces automatically. `GitHubPrDiffClient`'s GitHub API calls get simulated responses from corpus fixtures keyed by `GET /repos/{owner}/{repo}/pulls/{number}/files`.

### Scenario integration

Pages scenarios can drive end-to-end PR review flows with simulated LLM responses:

```yaml
simulation:
  strategies:
    agent-provider.invoke: sequential
  corpus:
    - fixtures/e2e-review-responses.yaml
```

### Adapter bridge tests

The function worker adapter in `PrReviewCaseHub` is the integration seam where engine dispatch, diff fetching, agent resolution, and outcome mapping converge. Test coverage follows the existing `PrReviewCaseHubMergeTest` pattern — direct unit tests of the adapter functions:

**`adaptReview()` tests (findings-based capabilities):**

| Test | Asserts |
|------|---------|
| `adaptReview_completed_withHighFinding_returnsRejected` | CRITICAL/HIGH finding → `WorkerResult.of(Map.of("outcome", "REJECTED", ...))` |
| `adaptReview_completed_allLowFindings_returnsApproved` | No CRITICAL/HIGH → `WorkerResult.of(Map.of("outcome", "APPROVED", ...))` |
| `adaptReview_declined_returnsApprovedOutcome` | Declined → `WorkerResult.of(Map.of("outcome", "APPROVED"))`, NOT `WorkerResult.declined()` |
| `adaptReview_failed_returnsWorkerFailed` | Failed → `WorkerResult.failed(reason)` |
| `adaptReview_noAgentRegistered_returnsFailed` | Missing capability → `WorkerResult.failed(...)` |
| `adaptReview_extractsFieldsFromInputMap` | Correct `repo`, `prNumber`, `headSha` extraction with type coercion |
| `adaptReview_diffCacheHit` | Second call with same `(repo, prNumber, headSha)` does not invoke `PrDiffService` |
| `adaptReview_diffServiceFailure_returnsWorkerFailed` | PrDiffService exception → `WorkerResult.failed(...)` via try-catch |
| `adaptReview_agentException_returnsWorkerFailed` | Agent throws → `WorkerResult.failed(...)` via try-catch |

**`adaptCodeAnalysis()` tests (classification):**

| Test | Asserts |
|------|---------|
| `adaptCodeAnalysis_returnsClassificationFields` | `WorkerResult.of(Map.of("complete", true, "securitySensitive", true, ...))` |
| `adaptCodeAnalysis_outputMatchesBindingConditions` | `.complete`, `.securitySensitive`, `.architectureCrossing` fields present at correct paths |
| `adaptCodeAnalysis_agentException_returnsWorkerFailed` | Agent throws → `WorkerResult.failed(...)` via try-catch |
| `adaptCodeAnalysis_noAgentRegistered_returnsFailed` | No `CodeAnalysisAgent` bean → `WorkerResult.failed(...)` |

Integration: YAML round-trip tests (extending `PrReviewCaseHubTest`) verify that capability bindings fire, function workers execute, and output projections produce correct context state.

### Maturity path

Start with curated fixtures (unit tests). Use capture mode on staging (`casehub.simulation.agent-provider.invoke.capture=true`) to build recorded corpus from real LLM responses. Replay in CI via `ci-replay` profile.

## References

- `review/src/main/java/.../ReviewerAgent.java` — port interface for findings-based capabilities (modified: `handle(ReviewContext)`, `priority()`)
- `review/src/main/java/.../CodeAnalysisAgent.java` — port interface for PR classification (new)
- `review/src/main/java/.../ReviewerOutcome.java` — sealed outcome type (modified: `List<ReviewFinding>`)
- `review/src/main/java/.../PrPayload.java` — PR payload record
- `review/src/main/java/.../PrReviewOutcome.java` — review result record (modified: `List<ReviewFinding>`)
- `app/src/main/java/.../agents/SecurityReviewAgent.java` — stub example
- `app/src/main/java/.../QhorusPrReviewService.java` — Layer 3 dispatch (modified: ReviewContext, registry lookup)
- `app/src/main/java/.../PrReviewCaseService.java` — Layer 5 dispatch
- `review/src/main/resources/devtown/pr-review.yaml` — CasePlanModel bindings
- `domain/src/main/java/.../ReviewDomain.java` — capability constants
- `docs/protocols/casehub/alternative-extension-patterns.md` — @DefaultBean displacement semantics (Pattern C: per-type, not per-bean)
- casehub-platform consumer-guide.md §Agent infrastructure — AgentProvider, manifest, model resolution
- casehub-eidos consumer-guide.md — AgentDescriptor, AgentCapability, modelRef
- casehub-blocks `StructuredAgentInvoker` — invoke-collect-parse utility (blocks#287)
- casehub-platform simulation framework — feat/294-simulation-service spec
- `docs/protocols/casehub/failure-cascade-pattern.md` — 4-tier failure cascade
- `ARC42STORIES.MD §9.4` — @DefaultBean displacement pattern (1:1 port pattern, not applicable to multi-instance)
