# Worker Reasoning Traces — Foundation Design

**Date:** 2026-08-21
**Issue:** casehubio/parent#421 (epic)
**Child issues:** casehubio/casehub-worker#13, casehubio/engine#950, casehubio/devtown#81
**Branch:** issue-421-worker-reasoning-traces
**Repos:** casehub-worker, casehub-engine, casehub-devtown

---

## Problem

CaseHub workers return outcomes (Success/Declined/Failed/Expired) but not
the reasoning behind their decisions. Gastown's `gt seance` lets agents
query prior session decisions and reasoning — devtown's `get_prior_decisions`
MCP tool returns outcomes only.

The missing capability is **reasoning trace storage and retrieval** —
capturing *why* a worker made a decision, not just *what* it decided.
This is a foundation capability: any CaseHub application's workers should
be able to emit and consume reasoning without custom plumbing.

## Architecture Overview

```
Worker returns WorkerResult(output, outcome, reasoning)
  → QuartzWorkerExecutionJob extracts reasoning
    → WorkflowExecutionCompleted carries reasoning field
      → WorkflowExecutionCompletedHandler
        → AgentExperienceRecorder.storeReasoning()
          → CaseMemoryStore.store(MemoryInput) [domain: "worker-reasoning"]

Next worker dispatched:
  → WorkerScheduleEventHandler
    → AgentMemoryRetriever.retrieve(workerName, tenantId, caseId, capability, def)
      → CaseMemoryStore.query() [domain: "worker-reasoning", entity: "case:<caseId>"]
        → WorkerContext.memories includes prior reasoning
```

---

## Part 1 — casehub-worker (worker#13)

### WorkerResult gains reasoning field

**Deviation from epic:** The epic (parent#421) and worker#13 place reasoning
on `WorkerOutcome`. This spec places it on `WorkerResult` instead. Reasoning
is a cross-cutting concern of the result, not a variant-specific property of
the outcome — adding a reasoning field to each of 5 sealed variants
duplicates identical state without architectural benefit. `WorkerResult` is
the wrapper that workers actually return; reasoning belongs there once. If
this design is accepted, worker#13's title and description should be updated
to reflect "extend WorkerResult" rather than "extend WorkerOutcome".

**Pre-implementation action:** Update worker#13 title from "add optional
reasoning field to WorkerOutcome" to "add reasoning field to WorkerResult
with withReasoning() copy method" before implementation begins, to avoid
misleading implementors who read the issue without this spec.

**Current:**
```java
public record WorkerResult<R>(R output, WorkerOutcome<R> outcome) { ... }
```

**After:**
```java
public record WorkerResult<R>(R output, WorkerOutcome<R> outcome, String reasoning) {
    public WorkerResult(R output, WorkerOutcome<R> outcome) {
        this(output, outcome, null);
    }
}
```

**Copy method** — reasoning is attached via `withReasoning()`:

```java
public WorkerResult<R> withReasoning(String reasoning) {
    return new WorkerResult<>(this.output, this.outcome, reasoning);
}
```

No new factory overloads. Callers attach reasoning explicitly:
```java
return WorkerResult.declined("reason").withReasoning("chain-of-thought...");
return WorkerResult.of(output).withReasoning("analysis narrative...");
```

This avoids type-erasure collision — the existing `declined(String reason,
R partialOutput)` erases to `declined(String, String)` when `R = String`,
which would conflict with a `declined(String reason, String reasoning)`
overload. The copy method eliminates all ambiguity and makes reasoning
attachment explicit at the call site. Original factories unchanged — all
existing callers compile without modification.

### WorkerOutcome unchanged

The sealed interface (`Success`, `Declined`, `Failed`, `Expired`, `Completed`)
is not modified. `Declined.reason()`, `Failed.reason()` remain short
explanations. Reasoning is the deeper narrative and lives on `WorkerResult`.

**Asymmetry with qhorus:** `QhorusMessageSignalBridge.handleWorkerOutcome()`
constructs `WorkerOutcome` directly from external protocol messages — it
never has a `WorkerResult` and does not carry reasoning. This is correct:
qhorus-originated outcomes are external message events (short reason strings),
not worker function returns with chain-of-thought reasoning.

---

## Part 2 — casehub-engine (engine#950)

**Divergence from engine#950 — retrieval path:** engine#950 describes
replacing `EmptyWorkerContextProvider` with a `CaseMemoryStore`-aware
implementation. Code investigation shows this is the wrong integration
point: `WorkerContextProvider.buildContext()` returns `WorkerContext`
(channels, prior workers, propagation context) — its return value is
discarded in `WorkerScheduleEventHandler` (line 148). Memory retrieval
flows through `AgentMemoryRetriever.retrieve()` (line 151), which
already queries `CaseMemoryStore` by domain and threads results into
`WorkerContext.memories` via `buildEventLog()`. This spec extends
`AgentMemoryRetriever` — the actual retrieval path — rather than
`WorkerContextProvider`. engine#950's description should be updated
to reflect this before implementation.

**Config default divergence:** engine#950 specifies "disabled by
default, enabled via property." This spec uses `defaultValue = "true"`
(enabled by default). This is intentional: the dual-gating design
(§3.3) means reasoning storage only occurs when workers actively call
`withReasoning()`, and retrieval requires per-case YAML opt-in via
`caseScopedDomains`. The engine-level gate is a kill-switch, not an
opt-in — defaulting to enabled is correct. engine#950 should be
updated to match.

### 2.1 Threading reasoning through the completion path

**WorkflowExecutionCompleted** gains a `String reasoning` field:

```java
public record WorkflowExecutionCompleted(
    CaseInstance caseInstance,
    Worker worker,
    String idempotency,
    Map<String, Object> output,
    String bindingName,
    WorkerOutcome outcome,
    UUID signalId,
    String workerCredentialToken,
    ExecutorRef executorRef,
    Map<String, Object> protocolMetadata,
    String reasoning                        // NEW
) { ... }
```

Existing convenience constructors pass `null` for reasoning.

**Construction sites** — all places that construct `WorkflowExecutionCompleted`:

1. **`QuartzWorkerExecutionJob.onSuccess()`** (scheduler-quartz) — has
   `WorkerResult`, extracts `workerResult.reasoning()` and passes to
   the constructor. This is the primary reasoning-carrying path.

   **Output replacement (LiveView bridge):** Before `onSuccess()` is
   called, `execute()` may replace the `WorkerResult` when the
   `ContextBridge.isLiveView()` output differs from the raw output:
   ```java
   var replaced = new WorkerResult(output, workerResult.outcome(),
       workerResult.reasoning());  // preserve reasoning
   ```
   The 3-arg constructor MUST be used here — using the 2-arg
   backward-compatible constructor would silently discard reasoning.

   **Scoped worker early-return:** `onSuccess()` has an early-return
   path for non-TRANSIENT scoped workers returning Success — it
   publishes `ScopedWorkerOutputEvent` and returns without publishing
   `WORKER_EXECUTION_FINISHED`. Reasoning must be stored before this
   early return. `ScopedWorkerOutputEvent` gains a `String reasoning`
   field:
   ```java
   public record ScopedWorkerOutputEvent(
       CaseInstance caseInstance, String workerName,
       Map<String, Object> output, String bindingName,
       UUID signalId, String reasoning) {}  // NEW field
   ```
   The `ScopedWorkerOutputHandler` initiates async reasoning
   storage via `agentExperienceRecorder.storeReasoning()` before
   applying the interim output — same fire-and-forget virtual
   thread pattern as the main completion path. The virtual thread
   is started (not awaited) before output application.
2. **`PersistentWorkerFunctionHandler.publishCompletion()`**
   (runtime/internal/executor/) — receives `WorkerOutcome<?>` directly
   (not `WorkerResult`). Persistent workers communicate via
   `PersistentScope`, which does not produce `WorkerResult`. Passes
   `null` for reasoning. Supporting reasoning for persistent workers
   requires extending the `PersistentScope` API — filed as follow-up.
3. **`QhorusMessageSignalBridge.handleWorkerOutcome()`**
   (runtime/internal/bridge/) — constructs `WorkerOutcome` from external
   protocol messages. No `WorkerResult`, no reasoning. Passes `null`.
4. **`WorkflowExecutionCompleted.approved()`** (common) — static factory
   for gate re-fire. Passes `null`. Reasoning was stored from the
   original event before the gate fork.
5. **`ActionGateApprovedHandler.refireCompletion()`** (runtime) — calls
   `approved()`. Passes `null` (gate re-fire path).
6. **`WorkResultSubmitter.complete()`** (testing) — test utility. Calls
   `approved()`. Passes `null`.

### 2.2 Reasoning emission — AgentExperienceRecorder extension

`AgentExperienceRecorder` gains:
- `Instance<CaseMemoryStore> caseMemoryStore` injection
- New public method `storeReasoning()`

```java
public void storeReasoning(
    CaseInstance caseInstance,
    String workerName,
    String capabilityName,
    WorkerOutcome<?> outcome,
    String reasoning,
    String bindingName) {

    if (!reasoningEnabled
        || !caseMemoryStore.isResolvable()
        || reasoning == null || reasoning.isBlank()) return;

    String truncated = truncateReasoning(reasoning);
    String outcomeKind = outcomeKindName(outcome);
    String effectiveCapability = capabilityName != null ? capabilityName : bindingName;
    Map<String, String> attributes = new HashMap<>(Map.of(
        "workerName", workerName,
        "capability", effectiveCapability,
        "bindingName", bindingName,
        "outcome", outcomeKind));
    if (truncated.length() < reasoning.length()) {
        attributes.put("truncated", "true");
    }

    MemoryInput input = new MemoryInput(
        "case:" + caseInstance.getUuid(),              // case-scoped entity
        WORKER_REASONING_DOMAIN,                       // dedicated domain
        caseInstance.tenancyId,
        caseInstance.getUuid().toString(),              // caseId for query filtering
        truncated,                                     // reasoning narrative
        Map.copyOf(attributes),
        resolveImportance(outcome, lookupConfig(caseInstance)));

    Thread.startVirtualThread(() -> {
        try {
            caseMemoryStore.get().store(input);
        } catch (Exception e) {
            LOG.warnf(e, "Reasoning trace storage failed for case=%s worker=%s — non-critical",
                caseInstance.getUuid(), workerName);
        }
    });
}

@ConfigProperty(name = "casehub.reasoning.enabled", defaultValue = "true")
boolean reasoningEnabled;

private static final MemoryDomain WORKER_REASONING_DOMAIN =
    new MemoryDomain("worker-reasoning");
private static final int DEFAULT_MAX_REASONING_LENGTH = 4096;
private static final String TRUNCATION_MARKER = "\n[...truncated...]\n";
```

**Called from WorkflowExecutionCompletedHandler** — single pre-fork call:

```java
void onWorkflowExecutionCompletedHandler(WorkflowExecutionCompleted event) {
    // ... credential revocation ...

    // Reasoning storage — outcome-independent, before the outcome fork.
    // Covers success, failure, AND gate paths in a single call site.
    if (event.reasoning() != null) {
        String capabilityTag = extractCapabilityTag(
            caseInstance, worker, event.bindingName());
        agentExperienceRecorder.storeReasoning(
            caseInstance, worker.name(), capabilityTag, event.outcome(),
            event.reasoning(), event.bindingName());
    }

    // Outcome fork: non-success outcomes → handleSemanticFailure()
    if (!(event.outcome() instanceof Success)
        && !(event.outcome() instanceof Completed)) {
        // ...
    }
    // Gate fork: planned action → handleWithPlannedAction()
    // ...
    // Success path continues
}
```

Reasoning storage is placed after credential revocation and before ALL
outcome forks. This is correct because:
- Reasoning is outcome-independent (as stated in the design intent)
- The gate fork (handleWithPlannedAction) re-fires via
  `WorkflowExecutionCompleted.approved()` which carries `null` reasoning
  — the original reasoning must be stored before the gate fork
- `extractCapabilityTag()` depends only on `caseInstance`, `worker`,
  and `bindingName` — all available from the event before any fork

**EventLog durability:** Reasoning is persisted in EventLog metadata on
all four completion paths so it is recoverable if the async
`CaseMemoryStore.store()` call fails. Each EventLog construction site
includes reasoning:

**Success path** — `buildMetadata()` gains a `String reasoning` parameter:
```java
private JsonNode buildMetadata(String idempotency, JsonNode contextDiff,
    Map<String, Object> protocolMetadata, String reasoning) {
    ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
    // ... existing metadata fields ...
    if (reasoning != null) {
        metadata.put("reasoning", reasoning);
    }
    return metadata;
}
```

**Failure path** — `handleSemanticFailure()` inline EventLog construction:
```java
ObjectNode failureMetadata = OBJECT_MAPPER.createObjectNode()
    .put("bindingName", bindingName)
    .put("reason", reason)
    .put("attempts", attempts)
    .put("disposition", /* ... */);
if (event.reasoning() != null) {
    failureMetadata.put("reasoning", event.reasoning());
}
eventLog.setMetadata(failureMetadata);
```

**Scoped worker path** — `ScopedWorkerOutputHandler.buildEventLog()`
gains a `String reasoning` parameter. The `ScopedWorkerOutputEvent`
carries reasoning, which flows through to EventLog metadata:
```java
private EventLog buildEventLog(
    CaseInstance caseInstance, String workerName,
    Map<String, Object> output, String bindingName,
    JsonNode contextDiff, String reasoning) {
    // ... existing EventLog fields ...
    ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
    if (bindingName != null) {
        metadata.put("bindingName", bindingName);
    }
    if (contextDiff != null) {
        metadata.set("contextChanges", contextDiff);
    }
    if (reasoning != null) {
        metadata.put("reasoning", reasoning);
    }
    eventLog.setMetadata(metadata);
    return eventLog;
}
```

**Gate path** — `buildGateEventLog()` gains a `String reasoning`
parameter. The gate is forked after the pre-fork `storeReasoning()`
call, but the EventLog provides backup durability in case the async
store fails:
```java
private EventLog buildGateEventLog(
    CaseInstance caseInstance, Worker worker,
    Map<String, Object> rawOutput, PlannedAction plannedAction,
    GateRequired gate, String idempotency, Instant timestamp,
    String reasoning) {
    // ... existing EventLog fields and payload ...
    ObjectNode metadata = OBJECT_MAPPER.createObjectNode()
        .put("inputDataHash", idempotency);
    if (reasoning != null) {
        metadata.put("reasoning", reasoning);
    }
    eventLog.setMetadata(metadata);
    return eventLog;
}
```

This covers all four primary paths uniformly: Success,
Declined/Failed/Expired, scoped worker interim output, and
gate-pending. Failure reasoning is at least as important as success
reasoning — a DECLINED worker's chain-of-thought about why it can't
handle the task is critical for diagnosing routing quality.

**Accepted exception — Autonomous re-fire:** When a worker returns a
`PlannedAction` that the `ActionRiskClassifier` classifies as
`Autonomous`, `handleWithPlannedAction()` re-fires via
`WorkflowExecutionCompleted.approved()` which carries `null` reasoning.
The re-fired event's success-path EventLog therefore has no reasoning.
This is acceptable because:

- The pre-fork `storeReasoning()` has already been called for the
  original event BEFORE `handleWithPlannedAction()` is entered — the
  async store is in-flight and covers this path
- The gap is only in the EventLog BACKUP mechanism, not the primary
  store — `storeReasoning()` succeeds in the common case
- Preserving reasoning in `approved()` would cause a duplicate
  `storeReasoning()` call on re-entry to the main handler, requiring
  re-fire detection logic for a narrow edge case
- The same applies to the concurrent-gate error path which also uses
  `approved()`

The reconciliation job follow-up should account for this: Autonomous
re-fire EventLog entries (identifiable by `outcome=Success` with no
`PlannedAction` and no `reasoning` in metadata, preceded by a
`WORKER_EXECUTION_FINISHED` event for the same worker) should source
reasoning from the original event's EventLog or from
`CaseMemoryStore` directly.

A background reconciliation job can replay EventLog entries with
`reasoning` metadata to re-store any traces that were lost during a
`CaseMemoryStore` outage. Reasoning in EventLog is a recovery
mechanism — `CaseMemoryStore` remains the primary queryable store.

**Async storage with EventLog backup (design decision):**
`CaseMemoryStore.store()` runs in a virtual thread (fire-and-forget)
to avoid blocking the worker completion path. This is a deliberate
choice — reasoning storage is not on the critical path for case
execution correctness. The case proceeds regardless of whether
reasoning is persisted to `CaseMemoryStore`.

The durability guarantee is provided by EventLog, not by the async
store. All four completion paths include reasoning in EventLog
metadata (see above). If the async `CaseMemoryStore.store()` fails,
reasoning is recoverable from EventLog via the reconciliation job.

**Trade-offs of async storage:**

- **Eventual visibility:** Reasoning from the most-recently-completed
  worker may not be visible when the immediately-next worker's
  retrieval query runs. In a case with workers A→B→C: when C is
  dispatched, A's reasoning is visible (stored well before C's
  retrieval query), B's reasoning may or may not be visible
  depending on timing.
- **Acceptable lag:** The value of reasoning traces is cumulative
  cross-worker context, not real-time streaming. A one-dispatch-cycle
  lag for the most recent trace does not degrade the feature's utility.
- **Read-after-write:** `CaseMemoryStore` implementations should still
  provide read-after-write consistency for completed writes — the
  only uncertainty is whether the async write has completed by the
  time the next query runs.

**Why not synchronous:** Both `WorkflowExecutionCompletedHandler` and
`ScopedWorkerOutputHandler` run `@RunOnVirtualThread`, so a synchronous
`CaseMemoryStore.store()` call would not block platform threads. But
it would add latency to the completion path — if `CaseMemoryStore` is
backed by a remote store (JPA, vector DB), each worker completion
would wait for that I/O. For a diagnostic/contextual feature where
the primary durability guarantee comes from EventLog, this latency is
unnecessary overhead.

**Reflection decoupling:** `storeReasoning()` does NOT call
`evaluateReflectionTrigger()`. Reasoning storage is independent of
experience recording and reflection accumulation. The `outcomeCount`
and `cumulativeImportance` accumulators in `evaluateReflectionTrigger()`
are not affected.

### 2.3 Reasoning trace format (D7)

*D-prefixed labels (D7, D8, D9) reference the companion design decisions
log at `decisions.md` in this spec directory.*

- **text:** Unstructured narrative — the worker's chain-of-thought as
  natural language. Good for vector embedding and SALIENCE-ordered queries.
- **attributes:** Structured metadata for filtering:
  - `workerName` — which worker produced this reasoning
  - `capability` — which capability was exercised
  - `outcome` — SUCCESS/COMPLETED/DECLINED/FAILED/EXPIRED
  - `bindingName` — which binding this was for
  - `truncated` — "true" if reasoning was truncated (omitted if not)

### 2.4 Size bounds (D9)

Reasoning traces are truncated to a configurable maximum (default 4096
characters) using dual-segment truncation:

- First `limit/3` characters (initial context — what was being analyzed)
- Truncation marker: `\n[...truncated...]\n`
- Last `2*limit/3` characters (conclusion and justification)

The 1:2 split ratio reflects that LLM chain-of-thought follows
premises → analysis → conclusion. Conclusions are more actionable for
subsequent workers than initial premises. Middle analysis (detailed
exploration of alternatives) is the least actionable content.

Truncation is indicated by `"truncated": "true"` in attributes.

```java
private String truncateReasoning(String reasoning) {
    if (reasoning.length() <= maxReasoningLength) return reasoning;
    if (maxReasoningLength <= TRUNCATION_MARKER.length() + 2) {
        return reasoning.substring(0, Math.min(reasoning.length(), maxReasoningLength));
    }
    int headLen = maxReasoningLength / 3;
    // Adjust to code point boundaries — avoid splitting surrogate pairs
    if (headLen > 0 && Character.isHighSurrogate(reasoning.charAt(headLen - 1))) headLen--;
    int tailLen = maxReasoningLength - headLen - TRUNCATION_MARKER.length();
    int tailStart = reasoning.length() - tailLen;
    if (tailStart < reasoning.length() && Character.isLowSurrogate(reasoning.charAt(tailStart)))
        tailStart++;
    return reasoning.substring(0, headLen)
        + TRUNCATION_MARKER
        + reasoning.substring(tailStart);
}
```

### 2.5 Importance and retention (D8)

Reasoning traces use outcome-based importance weights from
`ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS` as initial defaults:
SUCCESS=0.3, COMPLETED=0.3, DECLINED=0.5, FAILED=0.7, EXPIRED=0.5.

These defaults were originally calibrated for reflection triggering, not
reasoning retrieval. Deployments should tune importance weights for the
`"worker-reasoning"` domain independently. A successful security review's
reasoning (`APPROVED because X, Y, Z`) is highly actionable despite
SUCCESS=0.3 being low for reflection purposes.

Retention: `CaseMemoryStore.purge(MemoryRetentionPolicy)` with
domain=`"worker-reasoning"`, default `maxAgeDays=90`,
`minImportance=0.2`. Configurable per deployment.

### 2.6 Case-scoped retrieval — AgentMemoryRetriever extension

**MemoryRetrievalConfig** gains `caseScopedDomains`:

```java
public record MemoryRetrievalConfig(
    boolean enabled,
    int maxMemories,
    Set<String> domains,
    Set<String> caseScopedDomains,    // NEW — queried by "case:<caseId>"
    int maxCaseMemories               // NEW — separate budget for case-scoped
) {
    private static final Logger LOG = Logger.getLogger(MemoryRetrievalConfig.class);

    public MemoryRetrievalConfig {
        if (maxMemories < 1) throw new IllegalArgumentException("maxMemories must be >= 1");
        domains = domains == null ? Set.of() : Set.copyOf(domains);
        caseScopedDomains = caseScopedDomains == null ? Set.of() : Set.copyOf(caseScopedDomains);
        if (maxCaseMemories < 0) throw new IllegalArgumentException("maxCaseMemories must be >= 0");
        if (!caseScopedDomains.isEmpty() && maxCaseMemories == 0) {
            LOG.warnf("caseScopedDomains configured %s but maxCaseMemories=0 — "
                + "case-scoped retrieval is effectively disabled", caseScopedDomains);
        }
    }

    // Backward-compatible constructor — existing call sites compile unchanged
    public MemoryRetrievalConfig(boolean enabled, int maxMemories, Set<String> domains) {
        this(enabled, maxMemories, domains, Set.of(), 0);
    }

    public static MemoryRetrievalConfig defaults() {
        return new MemoryRetrievalConfig(false, 10, Set.of("experience", "reflection"), Set.of(), 0);
    }
}
```

**Known coupling:** `caseScopedDomains` encodes a retrieval semantic
(use `entityId = "case:<caseId>"`) in the case configuration. This is
intentional — different case types may want different domains to be
case-scoped. If a third scoping type appears (tenant-scoped,
binding-scoped), the config model should be revisited in favour of a
domain registry approach.

**Separate budgets:** `maxMemories` governs agent-scoped domains only
(the worker's own experience and reflections). `maxCaseMemories` governs
case-scoped domains (reasoning from other workers in the same case).
This prevents case-scoped reasoning from displacing a worker's own
memories. Case-scoped results are appended after agent-scoped results,
not interleaved.

**Call sites requiring update** (adding `caseScopedDomains` and
`maxCaseMemories` arguments):

| Call site | Current args | New args added |
|-----------|-------------|----------------|
| `MemoryRetrievalConfig.defaults()` | `(false, 10, Set.of("experience","reflection"))` | `Set.of(), 0` |
| `CaseDefinitionYamlMapper` (line ~982) | `new MemoryRetrievalConfig(enabled, max, domains)` | parse `caseScopedDomains` + `maxCaseMemories` from YAML |
| `MemoryRetrievalConfigTest` | direct constructor calls | add 4th/5th args |
| `AgentMemoryRetrieverTest` | direct constructor calls (4 test methods) | add 4th/5th args |

YAML:
```yaml
spec:
  memoryRetrieval:
    enabled: true
    maxMemories: 15
    maxCaseMemories: 5
    domains: [experience, reflection]
    caseScopedDomains: [worker-reasoning]
```

**AgentMemoryRetriever.retrieve()** gains `UUID caseId` parameter:

```java
public List<RetrievedMemory> retrieve(
    String workerName, String tenantId, UUID caseId,
    String capabilityName, CaseDefinition caseDefinition) {
    // ... existing agent-scoped queries for config.domains() ...
    // interleave agent-scoped results round-robin (unchanged)
    List<RetrievedMemory> agentMemories =
        interleaveRoundRobin(perDomainResults, config.maxMemories());

    // NEW: case-scoped queries — appended after agent-scoped
    if (caseId != null && !config.caseScopedDomains().isEmpty()
        && config.maxCaseMemories() > 0) {
        int caseBudget = config.maxCaseMemories();
        int perCaseDomainLimit = Math.max(1, caseBudget / config.caseScopedDomains().size());
        List<List<Memory>> caseDomainResults = new ArrayList<>();
        for (String domain : config.caseScopedDomains()) {
            List<Memory> memories = store.query(
                MemoryQuery.forEntity("case:" + caseId, new MemoryDomain(domain), tenantId)
                    .withQuestion(capabilityName)
                    .withLimit(perCaseDomainLimit)
                    .withOrder(MemoryOrder.SALIENCE));
            caseDomainResults.add(memories);
        }
        List<RetrievedMemory> caseMemories =
            interleaveRoundRobin(caseDomainResults, caseBudget);
        List<RetrievedMemory> merged = new ArrayList<>(agentMemories);
        merged.addAll(caseMemories);
        return List.copyOf(merged);
    }

    return List.copyOf(agentMemories);
}
```

**Call site update** in `WorkerScheduleEventHandler`:

```java
// Before:
agentMemoryRetriever.retrieve(worker.name(), instance.tenancyId, capability.name(), definition);
// After:
agentMemoryRetriever.retrieve(worker.name(), instance.tenancyId, instance.getUuid(),
    capability.name(), definition);
```

### 2.7 Entity ID convention

All reasoning traces use `entityId = "case:<caseId>"`. This means:

- **Same-case retrieval:** One query returns all workers' reasoning for a case.
  `MemoryQuery.forEntity("case:" + caseId, WORKER_REASONING, tenantId)`
- **Worker filtering:** `attributes.get("workerName")` in the returned `Memory`
  entries identifies which worker produced each trace.
- **Cross-case queries:** Not supported at the foundation level. Application
  layers (devtown MCP tools) handle cross-case queries via `MemoryScanRequest`
  or domain-specific entity schemes.

---

## Part 3 — casehub-devtown (devtown#81)

### 3.1 Dynamic MCP — `@McpDomain("devtown")` resolver

**Divergence from devtown#81:** devtown#81 specifies "New MCP tool:
`get_reasoning_trace` — query reasoning by case, capability, or entity."
This spec provides `caseReasoning()` and `contributorReasoning()` via
`@McpDomain("devtown")` + `@GraphQLApi`, routing through `casehub_action`
instead of a standalone `@Tool`. This is an intentional improvement:
(1) `@McpDomain` is the platform's forward direction, adopted by the
engine on `CaseMutationResolver`/`CaseQueryResolver`; (2) two focused
query methods are more discoverable than one generic tool with multiple
filter modes. Capability-based querying is deferred to the cross-case
module correlation follow-up. devtown#81 should be updated to reflect
the actual API shape.

New reasoning tools use the platform's `@McpDomain` + `@GraphQLApi`
pattern. This is devtown's first adoption of the pattern used by the
engine (`@McpDomain("engine")` on `CaseMutationResolver`/`CaseQueryResolver`).
The `@McpDomain` annotation is provided by `casehub-platform-api`; the
runtime scanning and registration is handled by `casehub-platform-graphql`.

Existing `@Tool` methods in `DevtownMcpTools` remain untouched — migration
of the 22+ existing tools is a separate issue.

**Dependency:** Devtown's `app/pom.xml` already depends on
`casehub-platform-api` (provides `@McpDomain`) and
`casehub-platform-graphql` (provides runtime scanning). No new
dependency is needed.

```java
@McpDomain("devtown")
@GraphQLApi
@ApplicationScoped
public class ReasoningQueryResolver {

    @Inject Instance<CaseMemoryStore> memoryStore;
    @Inject CurrentPrincipal principal;

    @Query
    @Description("Get worker reasoning traces for a specific case")
    public List<ReasoningTrace> caseReasoning(
        @Name("caseId") @Description("Case UUID") UUID caseId,
        @Name("limit") @Description("Max results (default 20)") @DefaultValue("20") int limit) {

        if (!memoryStore.isResolvable()) return List.of();
        var memories = memoryStore.get().query(
            MemoryQuery.forEntity("case:" + caseId,
                new MemoryDomain("worker-reasoning"), principal.tenancyId())
                .withLimit(limit)
                .withOrder(MemoryOrder.CHRONOLOGICAL));
        return memories.stream().map(ReasoningTrace::from).toList();
    }

    @Query
    @Description("Get reasoning traces for a contributor's PRs across cases — "
        + "looks up the contributor's review cases, then queries reasoning per case")
    public List<ReasoningTrace> contributorReasoning(
        @Name("contributor") @Description("GitHub username") String contributor,
        @Name("limit") @Description("Max results (default 20)") @DefaultValue("20") int limit) {

        if (!memoryStore.isResolvable()) return List.of();
        // Reasoning traces don't carry a contributor attribute (engine doesn't
        // know about contributors). Join through devtown's CaseMemoryStore
        // contributor facts → caseId → reasoning traces.
        String tenantId = principal.tenancyId();
        var contributorFacts = memoryStore.get().query(
            MemoryQuery.forEntity(
                DevtownMemoryDomain.CONTRIBUTOR_PREFIX + contributor,
                DevtownMemoryDomain.SOFTWARE_REVIEW, tenantId)
                .withLimit(limit)
                .withOrder(MemoryOrder.CHRONOLOGICAL));

        // Extract distinct caseIds from contributor review facts
        List<String> caseIds = contributorFacts.stream()
            .map(Memory::caseId).filter(Objects::nonNull).distinct()
            .limit(MemoryQuery.MAX_ENTITY_IDS).toList();

        if (caseIds.isEmpty()) return List.of();

        // Batch query: MemoryQuery.forEntities() supports up to 25 entityIds
        List<String> entityIds = caseIds.stream()
            .map(id -> "case:" + id).toList();
        var reasoning = memoryStore.get().query(
            MemoryQuery.forEntities(entityIds,
                new MemoryDomain("worker-reasoning"), tenantId)
                .withLimit(limit)
                .withOrder(MemoryOrder.CHRONOLOGICAL));
        return reasoning.stream().map(ReasoningTrace::from).toList();
    }

}
```

**moduleReasoning deferred:** A `moduleReasoning` query endpoint is NOT
included in the foundation. It requires a `module` attribute on reasoning
traces, which depends on devtown enriching `MemoryInput` attributes at
emission time — deferred to the cross-case module correlation follow-up
issue. The endpoint ships with that issue, not as dead code.

**ReasoningTrace** — read model for MCP responses:

```java
public record ReasoningTrace(
    String workerName,
    String capability,
    String outcome,
    String reasoning,
    Instant timestamp,
    boolean truncated
) {
    static ReasoningTrace from(Memory m) {
        return new ReasoningTrace(
            m.attributes().getOrDefault("workerName", "unknown"),
            m.attributes().getOrDefault("capability", "unknown"),
            m.attributes().getOrDefault("outcome", "unknown"),
            m.text(),
            m.createdAt(),
            "true".equals(m.attributes().get("truncated")));
    }
}
```

### 3.2 DevtownModelEnricher

Provides domain summary and state for `casehub_model` discovery:

```java
@McpDomain("devtown")
@ApplicationScoped
public class DevtownModelEnricher implements ModelEnricher {

    @Override
    public String summary() {
        return "Software engineering coordination — PR review, merge queue, "
            + "trust routing, reasoning traces";
    }

    @Override
    public Map<String, Object> state() {
        return Map.of("reasoningDomain", "worker-reasoning");
    }
}
```

### 3.3 CaseDefinition YAML update

Add `worker-reasoning` to the PR review case definition's memory retrieval:

```yaml
spec:
  memoryRetrieval:
    enabled: true
    maxMemories: 15
    maxCaseMemories: 5
    domains: [experience, reflection]
    caseScopedDomains: [worker-reasoning]
```

`maxCaseMemories` is required when `caseScopedDomains` is set — the
backward-compatible default is `0`, which disables case-scoped retrieval
even if domains are configured. Omitting it is a silent configuration
trap. The `MemoryRetrievalConfig` compact constructor logs a warning
when `caseScopedDomains` is non-empty but `maxCaseMemories` is 0.

**Dual config gating:** Reasoning has two independent configuration
gates that must be set together for the feature to function:

| Gate | Scope | Controls |
|------|-------|----------|
| `casehub.reasoning.enabled` | Engine-wide (application property) | Whether `storeReasoning()` emits to `CaseMemoryStore` |
| `caseScopedDomains` + `maxCaseMemories` | Per-case (YAML) | Whether retrieval queries `worker-reasoning` domain |

Both gates must be active for reasoning traces to be stored AND
consumed. `casehub.reasoning.enabled=true` without per-case YAML
configuration stores reasoning that is never queried. Per-case YAML
with `casehub.reasoning.enabled=false` queries an empty domain. The
engine-level gate enables gradual rollout; the per-case gate enables
selective adoption by case type.

### 3.4 Enhanced get_prior_decisions

The existing `get_prior_decisions` `@Tool` method in `DevtownMcpTools`
is enhanced to also query the `worker-reasoning` domain and include
reasoning alongside outcomes. The `PriorDecision` record gains an
optional `reasoning` field (nullable `String`).

**Record change:**

```java
public record PriorDecision(
    UUID caseId,
    String repo,
    int prNumber,
    String capability,
    String outcome,
    Instant decidedAt,
    String reasoning        // NEW — nullable, null when no reasoning stored
) {}
```

**Method change** — after querying `SOFTWARE_REVIEW` for outcomes,
extract distinct `caseId`s and batch-query `worker-reasoning` to join:

```java
@Tool(
    name = "get_prior_decisions",
    description = "Find prior review decisions for a specific repository and file path, with reasoning traces"
)
public List<PriorDecision> getPriorDecisions(
    @ToolArg(name = "repo", description = "Repository name", required = true) String repo,
    @ToolArg(name = "file_path", description = "File path within repo", required = true) String filePath
) {
    String tenant = principal.tenancyId();
    if (!memoryStoreInstance.isResolvable()) return List.of();

    var modules = ModulePathNormalizer.normalize(List.of(filePath));
    List<String> entityIds = modules.stream()
        .map(m -> DevtownMemoryDomain.MODULE_PREFIX + repo + "/" + m)
        .limit(MemoryQuery.MAX_ENTITY_IDS).toList();
    if (entityIds.isEmpty()) return List.of();

    CaseMemoryStore memoryStore = memoryStoreInstance.get();
    var outcomes = memoryStore.query(
        MemoryQuery.forEntities(entityIds, DevtownMemoryDomain.SOFTWARE_REVIEW, tenant)
            .withLimit(20).withOrder(MemoryOrder.CHRONOLOGICAL));

    // Join reasoning traces by caseId
    Map<String, String> reasoningByCaseId = Map.of();
    List<String> caseIds = outcomes.stream()
        .map(Memory::caseId).filter(Objects::nonNull).distinct()
        .limit(MemoryQuery.MAX_ENTITY_IDS).toList();
    if (!caseIds.isEmpty()) {
        List<String> reasoningEntityIds = caseIds.stream()
            .map(id -> "case:" + id).toList();
        var traces = memoryStore.query(
            MemoryQuery.forEntities(reasoningEntityIds,
                new MemoryDomain("worker-reasoning"), tenant)
                .withLimit(caseIds.size() * 5)
                .withOrder(MemoryOrder.CHRONOLOGICAL));
        reasoningByCaseId = traces.stream()
            .filter(m -> m.caseId() != null)
            .collect(Collectors.toMap(
                m -> m.caseId() + ":" + m.attributes().getOrDefault("capability", ""),
                Memory::text, (a, b) -> b));
    }

    Map<String, String> finalReasoningMap = reasoningByCaseId;
    return outcomes.stream()
        .map(m -> {
            String capability = m.attributes().getOrDefault("capability", "unknown");
            UUID caseUuid = m.caseId() != null ? UUID.fromString(m.caseId()) : null;
            int pr = Integer.parseInt(m.attributes().getOrDefault("pr-number", "0"));
            String key = m.caseId() != null ? m.caseId() + ":" + capability : null;
            String reasoning = key != null ? finalReasoningMap.get(key) : null;
            return new PriorDecision(caseUuid, repo, pr, capability, m.text(),
                m.createdAt(), reasoning);
        }).toList();
}
```

**Capability string alignment invariant:** The capability string stored
in reasoning trace attributes by the engine's `extractCapabilityTag()`
(returns `ct.capability().name()` from the binding's `CapabilityTarget`)
must match the capability string stored in `SOFTWARE_REVIEW` fact
attributes by devtown's `CaseMemoryEmitter` (uses
`ReviewCompletedEvent.capability()` via `ReviewOutcomeObserver.resolveCapability(bindingName)`).
Both resolve to the capability name from the case definition — the engine
via the binding model, devtown via a manual mapping. The manual mapping
in `resolveCapability()` is fragile: if new capability/binding name
divergences are added to the case definition, `resolveCapability()` must
be updated to match. The cross-case module correlation follow-up should
consider eliminating `resolveCapability()` in favour of sourcing the
capability name from the binding model directly.

The join key is `caseId + ":" + capability` — this matches a reasoning
trace to the outcome from the same worker in the same case. The merge
function `(a, b) -> b` keeps the **latest** reasoning trace per
case+capability (query uses `CHRONOLOGICAL` order, oldest first).
When multiple workers exercise the same capability for a case (e.g.,
worker A declines, worker B succeeds), this retains the resolution's
reasoning rather than the initial attempt. When no reasoning exists
for a given outcome (e.g., reasoning was disabled or the worker
didn't provide it), `reasoning` is null.

### 3.5 Cross-case correlation

Cross-case queries by module or contributor use `MemoryScanRequest`
(attribute-based scan) rather than `MemoryQuery.forEntity()`. This
works because:

- `workerName` is in attributes → scan by workerName for cross-case
  worker history
- Devtown could add a `module` attribute to reasoning traces (derived
  from the PR's `changedPaths` via `ModulePathNormalizer`) — this
  requires enriching the reasoning `MemoryInput` attributes at emission
  time. However, reasoning traces are emitted by the engine (which
  doesn't know about modules). Devtown would need to add module context
  either:
  1. Via a devtown-specific observer on the reasoning storage event
  2. By including module info in the worker's output that flows to
     the case context

This is deferred to a follow-up enhancement — the foundation provides
case-scoped reasoning retrieval; devtown cross-case correlation builds
on top.

---

## Module Placement

### casehub-worker

| Module | Changes |
|--------|---------|
| `worker-api` | `WorkerResult` gains `reasoning` field + `withReasoning()` copy method |

### casehub-engine

| Module | Changes |
|--------|---------|
| `common` | `WorkflowExecutionCompleted` gains `reasoning` field |
| `common` | `ScopedWorkerOutputEvent` gains `reasoning` field |
| `api/model` | `MemoryRetrievalConfig` gains `caseScopedDomains` + `maxCaseMemories` + config validation warning |
| `runtime` | `AgentExperienceRecorder` gains `storeReasoning()` + `CaseMemoryStore` injection |
| `runtime` | `AgentMemoryRetriever` gains `caseId` param + case-scoped query path |
| `runtime` | `WorkflowExecutionCompletedHandler` calls `storeReasoning()` pre-fork; `buildMetadata()`, `handleSemanticFailure()`, and `buildGateEventLog()` include reasoning in EventLog metadata |
| `runtime` | `ScopedWorkerOutputHandler` calls `storeReasoning()`; `buildEventLog()` includes reasoning in EventLog metadata |
| `scheduler-quartz` | `QuartzWorkerExecutionJob` extracts `reasoning` from `WorkerResult`, passes to `ScopedWorkerOutputEvent` and `WorkflowExecutionCompleted` |
| `schema` | YAML mapper parses `caseScopedDomains` + `maxCaseMemories` from case definition |

### casehub-devtown

| Module | Changes |
|--------|---------|
| `app` | `ReasoningQueryResolver` — `@McpDomain("devtown")` + `@GraphQLApi` |
| `app` | `DevtownModelEnricher` — domain summary and state |
| `app` | `ReasoningTrace` — read model record |
| `app` | `DevtownMcpTools` — `PriorDecision` gains `reasoning` field, `getPriorDecisions()` joins reasoning traces |
| `app/pom.xml` | No new dependency — `casehub-platform-api` + `casehub-platform-graphql` already present |
| `review` | PR review case definition YAML update |

---

## Testing

| Test | Type | Module | Verifies |
|------|------|--------|----------|
| `WorkerResultReasoningTest` | Unit | worker-api | `withReasoning()` copy method, null default, backward compat |
| `AgentExperienceRecorderReasoningTest` | Unit | engine/runtime | `storeReasoning()`: MemoryInput construction, truncation, attributes, CaseMemoryStore not resolvable → no-op, null/blank reasoning → no-op, failure swallowed |
| `ReasoningTruncationTest` | Unit | engine/runtime | Dual-segment truncation: under limit → unchanged, over limit → head + marker + tail, exact limit → unchanged, empty → no-op |
| `AgentMemoryRetrieverCaseScopedTest` | Unit | engine/runtime | Case-scoped domains query by `"case:<caseId>"`, agent-scoped domains query by workerName, interleaving, config without caseScopedDomains → no case queries |
| `MemoryRetrievalConfigTest` | Unit | engine/api | caseScopedDomains parsing, defaults, immutability |
| `CaseDefinitionYamlMapperTest` | Unit | engine/schema | Parses `caseScopedDomains` from YAML, missing → empty set |
| `ReasoningEmissionIntegrationTest` | `@QuarkusTest` | engine/runtime | Worker completes with reasoning → reasoning stored in CaseMemoryStore with correct entity/domain/attributes |
| `ScopedWorkerReasoningTest` | Unit | engine/runtime | `ScopedWorkerOutputHandler` receives `ScopedWorkerOutputEvent` with reasoning → calls `storeReasoning()`, EventLog metadata includes reasoning |
| `GatePathReasoningTest` | Unit | engine/runtime | Worker completes with reasoning + PlannedAction → pre-fork `storeReasoning()` called, gate EventLog metadata includes reasoning, re-fired `approved()` event skips duplicate storage |
| `ReasoningEventLogRecoveryTest` | Unit | engine/runtime | `storeReasoning()` virtual thread throws → EventLog contains reasoning in metadata → reconciliation replays it |
| `ReasoningRetrievalIntegrationTest` | `@QuarkusTest` | engine/runtime | Store reasoning → dispatch next worker → WorkerContext.memories contains reasoning trace. Verifies the AgentMemoryRetriever path (the actual memory retrieval path), not WorkerContextProvider. Satisfies devtown#81 AC "Foundation WorkerContextProvider enrichment verified" — the AC names the wrong class; the real verification is that subsequent workers receive prior reasoning via the memory retrieval pipeline. |
| `ReasoningQueryResolverTest` | `@QuarkusTest` | devtown/app | caseReasoning/contributorReasoning → correct Memory queries, empty store → empty list |
| `DevtownModelEnricherTest` | Unit | devtown/app | summary non-blank, state contains reasoningDomain |

---

## Build Order

1. **casehub-worker** — `WorkerResult` reasoning field (worker#13)
2. **casehub-engine** — depends on worker SNAPSHOT: emission, retrieval,
   `WorkflowExecutionCompleted` threading (engine#950)
3. **casehub-devtown** — depends on engine SNAPSHOT: MCP tools, YAML
   config, integration test (devtown#81)

---

## Follow-up Issues

**Filing status:** All items below will be filed as GitHub issues before
implementation begins. Issue numbers will be recorded here once filed.

**Epic scope note:** parent#421's acceptance criterion "Cross-case
correlation: query reasoning traces for the same entity across cases"
is deferred to the cross-case module correlation follow-up below. This
spec provides case-scoped reasoning retrieval and contributor-based
cross-case queries (`contributorReasoning()`), but module/code-area
cross-case correlation requires devtown-specific enrichment at emission
time that is outside the foundation scope. parent#421's AC should be
updated before implementation to remove cross-case module correlation
from this milestone. Similarly, devtown#81's AC "Cross-case correlation:
reasoning from prior reviews of the same code area surfaced" is deferred
to the follow-up.

- **Platform:** `@Tool` → `@McpDomain` migration for all existing devtown
  tools (file against devtown)
- **Devtown:** Cross-case module correlation — enrich reasoning MemoryInput
  with module attributes derived from PR changedPaths. Includes the
  `moduleReasoning` query endpoint (deferred from this foundation to
  avoid shipping a dead endpoint). This is the issue that unblocks
  closing parent#421's cross-case correlation AC.
- **Engine:** Importance weight tuning for `worker-reasoning` domain
  independent of reflection trigger weights
- **Engine:** `MemoryRetentionPolicy` defaults for `worker-reasoning`
  domain (maxAgeDays, minImportance)
- **Engine:** Reasoning support for persistent workers — extend
  `PersistentScope` API to support reasoning emission
- **Engine:** EventLog reasoning reconciliation job — replay EventLog
  entries with `reasoning` metadata to re-store missed CaseMemoryStore
  traces after outages. Must also handle Autonomous re-fire EventLog
  entries that lack reasoning metadata (see §2.2 accepted exception) —
  source reasoning from the original event's EventLog or from
  CaseMemoryStore directly

---

## References

- `WorkerResult.java` (casehub-worker-api) — reasoning host type
- `WorkerOutcome.java` (casehub-worker-api) — sealed interface, unchanged
- `WorkflowExecutionCompletedHandler.java` (engine/runtime) — emission call sites
- `AgentExperienceRecorder.java` (engine/runtime) — extended with storeReasoning()
- `AgentMemoryRetriever.java` (engine/runtime) — extended with caseId + caseScopedDomains
- `WorkerScheduleEventHandler.java` (engine/runtime) — retrieval call site
- `QuartzWorkerExecutionJob.java` (engine/scheduler-quartz) — reasoning extraction
- `QhorusMessageSignalBridge.java` (engine/runtime) — does NOT carry reasoning (asymmetry)
- `CaseMemoryStore.class` (neocortex-memory-api) — storage interface
- `MemoryInput.class` (neocortex-memory-api) — storage record
- `MemoryQuery.class` (neocortex-memory-api) — query API
- `casehub-platform-api` — provides `@McpDomain` annotation
- `casehub-platform-graphql` — provides runtime `@McpDomain` scanning and registration
- `CaseMutationResolver.java` (engine/graphql) — `@McpDomain("engine")` pattern
- `DevtownMcpTools.java` (devtown/app) — existing `@Tool` pattern (untouched)
- `ExperienceStream.class` (neocortex-memory) — ExperienceRecorder stores through CaseMemoryStore
- CaseMemoryStore Integration spec (devtown#43) — existing emission/recall architecture
- Agent Experience Recording spec (engine#800) — existing memory recording architecture
