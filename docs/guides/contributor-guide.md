# casehub-devtown -- Contributor Guide

> Internal architecture, SPIs, extension points, and implementation patterns for platform builders working on devtown internals.

**GitHub:** [casehubio/devtown](https://github.com/casehubio/devtown)

---

## Module Architecture

Six Maven modules with a strict dependency rule: domain has zero framework imports; Quarkus lives only in `app`.

```
domain  <--  review  <--  app
domain  <--  queue   <--  app
domain  <--  merge   <--  app
             github  <--  app
```

| Module | Tier | Dependency rule |
|--------|------|----------------|
| `domain` | Pure Java | Zero framework imports. No CDI, no JPA, no Quarkus. All constants, SPIs, and default implementations live here |
| `review` | Port layer | Port interfaces + YAML case definitions. Depends on domain + platform APIs (CaseMemoryStore, SubscribableEvent). No Quarkus runtime |
| `queue` | Pure Java | Merge queue domain logic. Zero framework imports |
| `merge` | Port layer | Merge queue port interfaces. Pure Java |
| `github` | Adapter layer | GitHub-specific adapters. Depends on domain + review ports. Uses Quarkus REST client |
| `app` | Runtime assembly | All CDI wiring, REST resources, MCP tools, persistence, notification bridges. Depends on everything |

---

## CDI Displacement Chain

Three implementations of `PrReviewApplicationService` coexist at all times. CDI priority determines which is active:

```java
// Layer 1 -- fallback, always present
@ApplicationScoped @DefaultBean
class PrReviewService implements PrReviewApplicationService

// Layer 3 -- present; CDI-inactive in full build; injectable by concrete type in tests
@ApplicationScoped @Alternative @Priority(1)
class QhorusPrReviewService implements PrReviewApplicationService

// Layer 5 -- ACTIVE in full build; wins CDI selection
@ApplicationScoped @Alternative @Priority(2)
class PrReviewCaseService implements PrReviewApplicationService
```

**Key rule:** Both competing non-`@DefaultBean` beans must carry `@Alternative @Priority(N)`. Without `@Alternative` on both, CDI treats them as ambiguous and fails at startup.

**Test injection trick:** `@Inject QhorusPrReviewService service` (concrete type) bypasses CDI priority ordering. This lets tests exercise Layer 3 without a test profile, even when Layer 5 is the CDI winner.

---

## PrReviewCaseDefinition

`PrReviewCaseDefinition` (in `review/src/main/`) is the fluent DSL factory for the PR review case. Uses `LambdaExpressionEvaluator` for binding conditions and `HumanTaskTarget.inline()` for human-approval binding.

`PrReviewCaseDefinitionEquivalenceTest` verifies structural parity between the fluent DSL and the YAML definition. `PrReviewBindingConditionTest` covers all 9 binding conditions with 28 pure unit tests (no Quarkus).

---

## Trust-Weighted Routing

### Activation

Adding `casehub-engine-ledger` to `app/pom.xml` is sufficient. The module ships `TrustWeightedAgentStrategy @Alternative @Priority(1)` (beats `LeastLoadedAgentStrategy @Priority(0)`) and `WorkerDecisionEventCapture` (writes `WorkerDecisionEntry` to the ledger on each worker completion).

### DevtownTrustRoutingPolicyProvider

`DevtownTrustRoutingPolicyProvider @ApplicationScoped` (no `@DefaultBean`) displaces `DefaultTrustRoutingPolicyProvider @DefaultBean` automatically. Reads threshold/minimumObservations/borderlineMargin from `DevtownCapabilityRegistry` (single source of truth) and supplements with blendFactor and quality floors from YAML via `casehub-platform-config`.

YAML at `app/src/main/resources/casehub/devtown/trust-routing.yaml` carries only engine-specific fields -- the three base fields come from the registry.

Per-capability policies:

| Capability | threshold | minObs | margin | blendFactor | qualityFloors |
|---|---|---|---|---|---|
| `security-review` | 0.70 | 10 | 0.05 | 0.70 | `review-thoroughness >= 0.60` |
| `architecture-review` | 0.65 | 8 | 0.05 | 0.70 | `review-thoroughness >= 0.60` |
| `style-review` | 0.50 | 5 | 0.0 | 0.50 | -- |
| `merge-executor` | 0.80 | 15 | 0.05 | 0.80 | `precision >= 0.70` |

### Qhorus Trust Gate

`DevtownObligorTrustPolicy @ApplicationScoped` applies a minimum trust floor of 0.30 with bootstrap exemption: agents with `Optional.empty()` trust score (no observations) are always permitted. Agents with a score below 0.30 are refused.

---

## Trust Feedback Closed Loop

`report_incident` MCP tool (`DevtownMcpTools`): reports a production incident against a merged PR -- writes FLAGGED attestation against the reviewer's trust score.

Parameters: repository, prNumber, incidentId, severity (LOW/MEDIUM/HIGH/CRITICAL), description, reviewCapability, caseId (optional).

Chain: `IncidentFeedbackService` resolves merge decision from ledger, finds worker decisions, writes FLAGGED attestations with trust dimension `REVIEW_THOROUGHNESS`. `TrustFeedbackClosedLoopTest` provides E2E proof of the full chain.

---

## EvidentialChecker Integration

`EvidentialAttestationPolicy` (`@Alternative @Priority(2)`) consumes `EvidentialChecker` from `io.casehub.qhorus.runtime.audit`. Runs four benchmark variants:

| Variant | What it checks |
|---------|---------------|
| V1 | Artefact check |
| V2 | Channel check |
| V3 | Correlation check |
| V4 | Token check with content |

Checks run only for configured phases per capability. `EvidentialViolationStore` stores violation records in memory. MCP tool `get_evidential_violations` lists violations from FLAGGED attestations.

---

## CBR Implementation Details

### PR Similarity Model (domain/cbr/)

- `PrFeatureVector` -- structured feature extraction: file paths (normalized modules via `ModulePathNormalizer`), languages (inferred from extensions), change size bucket, contributor
- `WeightedJaccardSimilarity` implements `SimilarityMetric` -- 5-dimension weighted scoring returning `SimilarityScore` with per-dimension `breakdown` map
- `SimilarityGate` -- hard minimum overlap filters before scoring (devtown#143). Prevents low-overlap high-weight dimensions from inflating scores
- `Precedent` -- past case with `SimilarityScore`, feature vector, `Map<String, CapabilityOutcome>`, outcome string, and optional `Duration completionTime`
- `CapabilityOutcome` -- outcome, detail, timestamp per capability with `hadFindings()` check

### Retrieval (app/)

`DefaultCbrRetrievalService` implements `CbrRetrievalService`:
1. Scans case-vector memories from `CaseMemoryStore`
2. Applies `SimilarityGate` filters
3. Scores with `WeightedJaccardSimilarity`
4. Enriches with `CapabilityOutcome` data from stored memories
5. Computes completion times from memory timestamps

**Known gap:** Uses `CaseMemoryStore.scan()` (not `CbrCaseMemoryStore`) due to four platform gaps; migration path to `CbrCaseMemoryStore` when neocortex gains `FeatureField.SetValued`.

### Precedent Activation (domain/cbr/)

`PrecedentActivationPolicy` fires review capabilities based on precedent similarity:
- Per-capability `ActivationThreshold` with minimum similarity and sample count
- `activationSource` attribution on content-triggered bindings (devtown#148) -- tracks whether a binding fired from content analysis or precedent
- `CbrWeightAdjuster` -- dynamic weight adjustment from outcome feedback (devtown#138). Adjusts per-dimension weights based on whether precedent-based routing produced better outcomes

### CBR in Reviewer Matching (app/routing/)

`DevtownTrustRoutingPolicyProvider` provides per-capability CBR weights. `cbrWeight` on `TrustRoutingPolicy` is consumed by engine-ledger's `TrustWeightedAgentStrategy` via `AgentRoutingContext.experiences()`. Populated from `RetrievedExperience` and `ExperiencePlanStep`.

`CbrReviewerMatchingIntegrationTest` proves an agent with lower trust but higher precedent match wins over a higher-trust agent with no precedent.

### CBR Configuration (domain/cbr/)

`CbrPreferenceKeys` preference keys:
- `K_LIMIT` -- max precedents to retrieve
- `MIN_THRESHOLD` -- minimum similarity score
- `TIME_WINDOW_DAYS` -- lookback window
- Per-dimension weight keys for each similarity dimension

---

## SLA Calibration

### Domain (domain/sla/)

- `SlaEstimator` -- computes `SlaEstimate` from `List<Precedent>` using median completion times
- `SlaEstimate` -- `DurationStats overall` + `Map<String, DurationStats> capabilityBreakdown` (devtown#152)
- `DurationStats` -- median, min, max, sampleCount with `toMap()` for case context injection

### Persistence (app/persistence/)

- `SlaCalibrationStore` interface (review module)
- `SlaCalibrationRecord` -- capability, scopePath, median/min/max, precedentCount, caseId, computedAt
- `JpaSlaCalibrationStore` -- JPA implementation with `SlaCalibrationEntity`

### Override

SLA override preference keys (devtown#151) allow calibrated estimates to replace configured SLAs. Context write (devtown#151) persists override values.

---

## CaseMemoryStore Integration

### Memory Lifecycle

1. **Before case start:** `CaseMemoryRecaller` queries `CaseMemoryStore` for contributor history (`CONTRIBUTOR_PREFIX + contributor`), code-area history (`MODULE_PREFIX + repo/module`), and CBR precedents. Returns `MemoryContext`
2. **At case open:** `FeatureVectorEmitter` stores `PrFeatureVector` as a memory fact for future CBR retrieval
3. **At case close:** `ReviewOutcomeObserver` observes `PlanItemCompletedEvent`, fires `ReviewCompletedEvent`, which `CaseMemoryEmitter` handles by calling `storeAll()` with review outcomes

### Memory Domain Types (domain/memory/)

- `DevtownMemoryDomain` -- domain constants: `SOFTWARE_REVIEW`, `CONTRIBUTOR_PREFIX` ("contributor:"), `MODULE_PREFIX` ("module:")
- `DevtownMemoryKeys` -- attribute key constants (CAPABILITY, OUTCOME_DETAIL, etc.)
- `MemoryRecallKeys` -- preference keys for recall configuration
- `ModulePathNormalizer` -- normalizes file paths to module-level groupings for area-based memory lookup
- `ReviewOutcome` -- enum for memory outcomes (SUCCESS, FAILED, etc.)

### MemoryContext (review/)

`MemoryContext` record aggregates:
- `contributorHistory` -- `List<Memory>` for the PR author
- `codeAreaHistory` -- `List<Memory>` for changed modules
- `precedents` -- `List<Precedent>` from CBR retrieval
- `precedentActivations` -- `Set<String>` of capabilities activated by precedent

`hasRiskSignals()` checks for FAILED outcomes or findings in history. `toContextMap()` serializes for case context injection.

---

## Coordinated Change Architecture

### Domain Types

- `CoordinatedChangeRequest` -- carries `List<RepoChangeEntry>`
- `RepoChangeEntry` -- owner, repo, prNumber, headSha, targetBranch, contributor, changedPaths, linesChanged
- `CoordinatedChangeOutcome` -- parentCaseId + `Map<String, UUID>` of repo-to-case mappings
- `CoordinatedMergeResult` -- merge outcomes per repo

### Ports

- `CoordinatedChangePort` (review module) -- `start(CoordinatedChangeRequest) -> CoordinatedChangeOutcome`
- `MergeClient` (domain) -- `merge(String owner, String repo, int prNumber) -> MergeOutcome`
- `RevertClient` (domain) -- `revert(String owner, String repo, int prNumber) -> RevertOutcome`

### App Layer

- `CoordinatedChangeCaseHub` -- loads `coordinated-change.yaml` CasePlanModel
- `CoordinatedChangeService` implements `CoordinatedChangePort` -- starts parent case, starts per-repo sub-cases, registers tracking
- `CoordinatedChangeTracker` -- in-memory tracking of parent -> sub-case relationships
- `CoordinatedChangeTrackerHydrator` -- startup hydration from durable state (devtown#162)
- `CoordinatedChangeObserver` -- observes sub-case completion/fault, signals parent case. Fixed: correctly classifies failure-goal COMPLETED vs TERMINAL_SUCCESS (devtown#161)

Workers:
- `CoordinatedMergeWorker` -- merges all repos when sub-cases COMPLETED (devtown#157)
- `CoordinatedRollbackWorker` -- reverts merges on sub-case FAULT (devtown#158) using `RevertClient`

### Case Definitions

- `coordinated-change.yaml` -- parent case: waits for all sub-cases, triggers merge or rollback
- Individual repo sub-cases use `pr-review.yaml` with `coordinatedChange: true` context flag

### Tests

- `CrossRepoCoordinatedMergeTest` -- end-to-end integration test (devtown#160)
- `CoordinatedChangeObserverTest`, `CoordinatedChangeServiceTest`, `CoordinatedMergeWorkerTest`, `CoordinatedRollbackWorkerTest`

---

## Action Risk Classification

`DevtownActionRiskClassifier` implements `ActionRiskClassifier` SPI from engine (engine#402).

Eight `DevtownActionType` constants, four classification categories. `DevtownRiskClassifierProducer` (`@RiskClassifier @ApplicationScoped`) CDI adapter.

PreferenceProvider-driven thresholds at scope `casehubio/devtown/risk/<actionType>`. Gate operates through engine's `ActionGateWorkItemHandler` lifecycle (classifier evaluates -> PendingActionGate -> WorkItem for human approval -> resume).

`BooleanPreference` and `RiskPreferenceKeys` in `domain/preferences/`. `HumanOversight.GENERAL` added as catch-all oversight group.

No new REST endpoints -- operates entirely through the engine's existing gate infrastructure.

---

## GitHub Integration Details

### Webhook Handler

`GitHubWebhookResource` at `POST /api/github/webhook`:
- HMAC-SHA256 signature verification via `GitHubSignatureVerifier`
- Routes events to `PrReviewApplicationService` lifecycle methods:
  - `pull_request.opened/synchronize` -> `startReview()` / `revisePr()`
  - `pull_request.closed` -> `closePr()`
  - `check_suite.completed` -> `signalCiStatus()`
  - `check_run.completed` -> `signalCheckRun()`
- `GitHubPayloadMapper` maps webhook JSON to `PrPayload` / `GitHubPullRequestEvent` / `GitHubCheckSuiteEvent` / `GitHubCheckRunEvent`

### REST Clients

- `GitHubPullRequestApi` -- PR operations via `@RegisterRestClient`
- `GitHubMergeApi` -- merge operations
- `GitHubChecksApi` -- check suite/run queries
- `GitHubGitApi` -- git ref operations for batch branches

### Adapters

- `GitHubCiStatusClient` implements `CiStatusClient` -- aggregates check suite status into `CombinedCiStatus`
- `GitHubMergeClient` implements `MergeClient` -- returns `MergeOutcome`
- `GitHubRevertClient` implements `RevertClient` -- PR-based merge undo, returns `RevertOutcome`
- `GitHubBatchBranchClient` implements `BatchBranchClient` -- batch branch CRUD, returns `BatchBranchOutcome` / `BranchDeleteOutcome`

---

## Notification Architecture

### Event Types (review/notification/)

Seven `SubscribableEvent` implementations. Each carries a `type()` string and `tenancyId()`.

### Subscription Registration

`DevtownSubscriptionRegistrar` registers seven system-scope subscriptions at startup. Each subscription specifies:
- Event type to subscribe to
- Notification targets (event field, group, entity watchers)
- `NotificationTemplate` with title, body, severity, category, link URL, entity type, entity ID field, actor ID field

### Notification Bridges (app/notification/)

Five bridge classes translate platform CDI events to devtown `SubscribableEvent` types:

| Bridge | Platform event | DevTown event |
|--------|---------------|---------------|
| `AgentDispatchNotificationBridge` | `MessageObserver` commitment events | `AgentReviewDispatchedEvent` |
| `ReviewAssignmentNotificationBridge` | `WorkItemLifecycleEvent` | `ReviewAssignedEvent` |
| `CaseLifecycleNotificationBridge` | `CaseLifecycleEvent` FAULTED | `CaseFaultedEvent` |
| `SlaBreachNotificationBridge` | `SlaBreachEvent` | `SlaEscalatedEvent` |
| `WatchdogAlertNotificationBridge` | Watchdog timer | `StalledCommitmentEvent` |

---

## Merge Queue Internals

### Queue Module (queue/)

Pure Java domain logic:

| Class | Purpose |
|-------|---------|
| `QueuedPr` | Record: prNumber, repo, headSha, author, trustScore, PriorityLane, enqueuedAt, dependencies |
| `QueuePriorityCalculator` | Priority scoring: `lane_weight * 1000 + trust * 100 + wait_decay` |
| `DefaultBatchCompositionPolicy` | Batch formation from queue: priority sort, risk-aware grouping, adaptive max sizing |
| `BatchCompositionPolicy` | Interface for batch formation |
| `BatchFormationContext` | Context for batch formation decisions |
| `DependencyResolver` | Resolves inter-PR dependencies for ordering |
| `BisectionSplitStrategy` | Interface for all bisection strategies |
| `BinarySplitStrategy` | Simple binary split |
| `PrecedentBisectionStrategy` | Risk-score-sorted, high-risk in left slice |
| `TrustWeightedSplitStrategy` | Trust-weighted split |
| `IsolateOutlierStrategy` | Outlier isolation based on risk score |
| `CbrBatchRiskAssessor` | CBR-based batch risk scorer using `CaseMemoryStore` precedents |
| `BatchRiskAssessor` | Interface for batch risk assessment |
| `Batch` | Record: id, PRs, formation metadata |
| `BatchSlice` | Record: slice of a bisected batch |
| `SplitResult` | Record: bisection result with left/right slices |

### Merge Module (merge/)

Port interfaces and records:

| Class | Purpose |
|-------|---------|
| `MergeQueuePort` | Hexagonal port for merge queue operations |
| `MergeQueueStore` | Persistence SPI: save/load batches and entries |
| `QueueEntry` | Record: queue entry with status |
| `QueueEntryStatus` | Enum: QUEUED, BATCHED, MERGED, REJECTED, etc. |
| `BatchRecord` | Record: batch metadata |
| `AdmissionResult` | Record: admission decision result |

Case definitions:
- `merge-batch.yaml` -- batch lifecycle: formation, CI run, bisection on failure, merge execution
- `merge-queue-entry.yaml` -- per-PR lifecycle within the queue

### App Layer

| Class | Purpose |
|-------|---------|
| `MergeQueueService` | Central merge queue orchestration, failure rate calculation, alert evaluation |
| `MergeBatchCaseHub` | Loads `merge-batch.yaml` CasePlanModel |
| `MergeQueueEntryCaseHub` | Loads `merge-queue-entry.yaml` CasePlanModel |
| `MergeBatchCompletionObserver` | Observes batch case completion/fault |
| `MergeQueueSlaBreachObserver` | SLA breach handling for queue entries |
| `PrReviewMergeQueueAdapter` | Bridges PR review completion to merge queue enqueue |
| `MergeQueueProducers` | CDI producers for queue dependencies |
| `BatchRetentionJob` | Scheduled cleanup: `@Scheduled(cron = "0 0 3 * * ?")`, default 30-day retention |
| `BatchBranchCleanupObserver` | Cleanup of batch branches after batch completion |
| `FailureRateAlertEvent` | CDI event for sustained high failure rate alerts (devtown#109) |
| `CbrWeightOverrideStore` | Stores CBR weight overrides from outcome feedback |

### Persistence (app/persistence/)

| Class | Purpose |
|-------|---------|
| `JpaMergeQueueStore` | JPA implementation of `MergeQueueStore` |
| `BatchEntity` | JPA entity for batch records |
| `QueuedPrEntity` | JPA entity for queued PR entries |
| `JpaSlaCalibrationStore` | JPA implementation of `SlaCalibrationStore` |
| `SlaCalibrationEntity` | JPA entity for SLA calibration records |

---

## Compliance Architecture

### Domain Types (review/compliance/)

| Type | Purpose |
|------|---------|
| `CodeReviewComplianceEvidence` | Aggregate compliance record for a case |
| `AuditChainRequirement` | Tamper-evident ledger chain integrity evidence |
| `ReviewSlaRequirement` | SLA compliance -- whether deadlines were met |
| `TrustRoutingRequirement` | Trust-weighted routing compliance -- from `casehub-blocks` |
| `GdprRequirement` | GDPR erasure state for involved actors |
| `ErasureReceipt` | Proof of GDPR Art.17 erasure completion |
| `InclusionProofRecord` | Merkle inclusion proof from ledger |
| `LedgerEventRecord` | Ledger event data for compliance chain |

### Service (app/ledger/)

`CodeReviewComplianceService` produces `CodeReviewComplianceEvidence` by aggregating data from ledger (audit chain, Merkle proofs), work module (SLA compliance), engine-ledger (trust routing), and identity service (GDPR state).

`DevtownComplianceSupplement` provides devtown-specific compliance data to the blocks framework.

---

## Ledger Integration (app/ledger/)

| Class | Purpose |
|-------|---------|
| `IncidentFeedbackService` | Records FLAGGED attestations from production incidents. Resolves merge decision from ledger, finds worker decisions, writes attestations with trust dimension `REVIEW_THOROUGHNESS`. Idempotent via `findAttestationsByAttestorIdAndCapabilityTag` |
| `GdprErasureService` | GDPR Art.17 erasure: pseudonymises actor identity in ledger, cleans `CaseMemoryStore` (`contributor:` + `reviewer:` prefixes), persists tamper-evident `ErasureReceiptLedgerEntry`. SHA-256 hash fallback when no `ActorIdentity` mapping exists |
| `MergeDecisionObserver` | Observes case completion for merge decisions, writes `MergeDecisionLedgerEntry` |
| `MergeDecisionLedgerEntry` | Tamper-evident ledger entry for merge decisions with SHA-256 content hash |
| `LedgerContentUtils` | Utility for ledger content serialization |

---

## Case Tracker (app/mcp/)

`PrReviewCaseTracker` maintains an in-memory registry of active PR review cases:
- `register(caseId, tenantId, PrPayload)` -- register a new case
- `getCase(caseId)` -- retrieve case info
- `findActiveCaseByPr(repo, prNumber)` -- find active case for a PR
- Ring buffer of `TrackedEvent` for recent event history

`PrReviewCaseTrackerHydrator` handles startup hydration from durable case state (devtown#127).

`CaseInfo` record: caseId, tenantId, PrPayload, status. `CaseTrackingStatus` record: tracking state.

---

## SPI Extension Points

### Domain SPIs (domain/spi/)

| SPI | Purpose | Default |
|-----|---------|---------|
| `CapabilityRegistry` | `capabilities()`, `policy(String)`, `isKnown(String)` default method | `DevtownCapabilityRegistry` -- 14 capabilities, 4 routing policies |

### Review SPIs (review/)

| SPI | Purpose |
|-----|---------|
| `PrReviewApplicationService` | Port interface for PR review lifecycle (5 methods) |
| `CbrRetrievalService` | CBR precedent retrieval |
| `CoordinatedChangePort` | Cross-repo coordinated change |
| `ReviewerAgent` | Driven-port for specialist reviewers: `capability()` + `handle(PrPayload)` |
| `SlaCalibrationStore` | SLA calibration persistence |

### Merge SPIs (merge/)

| SPI | Purpose |
|-----|---------|
| `MergeQueuePort` | Merge queue operations |
| `MergeQueueStore` | Merge queue persistence |

### Queue SPIs (queue/)

| SPI | Purpose |
|-----|---------|
| `BatchCompositionPolicy` | Batch formation strategy |
| `BatchRiskAssessor` | Batch risk scoring |
| `BisectionSplitStrategy` | Bisection approach for failing batches |

### Domain Client SPIs (domain/)

| SPI | Purpose | NoOp default |
|-----|---------|-------------|
| `BatchBranchClient` | Git operations for batch branches | `NoOpBatchBranchClient` |
| `CiStatusClient` | CI status queries | `NoOpCiStatusClient` |
| `MergeClient` | PR merge execution | `NoOpMergeClient` |
| `RevertClient` | Merge revert for rollback | `NoOpRevertClient` |

All NoOp defaults are `@DefaultBean` in `app/spi/` -- displaced by real implementations (e.g. `GitHubMergeClient`) when the `github` module is on the classpath.

---

## Reviewer Agent Stubs (app/agents/)

Four in-process stub agents implementing `ReviewerAgent`:

| Agent | Capability | Behavior |
|-------|-----------|----------|
| `SecurityReviewAgent` | `security-review` | Returns `Completed` with rate-limiting finding |
| `ArchitectureReviewAgent` | `architecture-review` | Returns `Declined` with reason: "distributed transaction outside scope" |
| `TestCoverageReviewAgent` | `test-coverage` | Returns `Completed` with coverage finding |
| `PerformanceAnalysisAgent` | `performance-analysis` | Stub |

These are placeholders for future Claudony agents. The CDI wiring, channel structure, and commitment lifecycle are production-correct regardless of whether agents are in-process or out-of-process.

`ReviewerOutcome` is a sealed interface: `Completed(List<String> findings)` + `Declined(String reason)`.

---

## Preference System

### Domain Preference Keys

| Key class | Scope | Purpose |
|-----------|-------|---------|
| `SlaPreferenceKeys` | `domain/sla/` | CANDIDATE_GROUP, ESCALATION_GROUP, ESCALATION_HOURS, COMPLETION_HOURS, BREACH_TERMINAL_REASON |
| `PrReviewPreferenceKeys` | `domain/preferences/` | PR review policy configuration |
| `RiskPreferenceKeys` | `domain/preferences/` | Action risk classification thresholds |
| `CbrPreferenceKeys` | `domain/cbr/` | CBR retrieval configuration: K_LIMIT, MIN_THRESHOLD, TIME_WINDOW_DAYS, weights |
| `MergeQueuePreferenceKeys` | `domain/queue/` | Merge queue batch formation and SLA configuration |
| `TrustGatePreferenceKeys` | `domain/trust/` | Trust gate minimum floor and bootstrap exemption |
| `NotificationPreferenceKeys` | `domain/notification/` | Notification routing configuration |
| `MemoryRecallKeys` | `domain/memory/` | Memory recall configuration |

### Preference Types

- `BooleanPreference` -- `domain/preferences/`
- `DoublePreference` -- `domain/preferences/`
- `IntPreference` -- `domain/preferences/`
- `StringPreference` -- `domain/sla/`

---

## Key Design Decisions

### Port interface placement

`PrReviewApplicationService`, `PrPayload`, and `PrReviewOutcome` live in `review/` (not `app/`). If the port lived in `app/`, any `review`-module implementation would create a module dependency cycle. Fixed in commit `18b22e0`.

### Vocabulary split over flat tags

Gastown's 13 flat capability tags became four typed classes (`ReviewDomain`, `AgentQualification`, `HumanDecision`, `HumanOversight`) with distinct routing semantics. `BATCH_BISECT` / `COORDINATED_MERGE` / `COORDINATED_ROLLBACK` were removed from capability tags -- they are orchestration operations expressed as CasePlanModel binding structures, not trust-scored agent assignments.

### CapabilityRegistry as populated default, not no-op

`DevtownCapabilityRegistry` is a vocabulary/registry SPI (populated default), not an operational no-op SPI (empty default). `CapabilityRegistryBean` is a one-liner `@ApplicationScoped` subclass that promotes it to CDI.

### Stateless SLA escalation

`DefaultSlaBreachPolicy` reads `candidateGroups` from the expired WorkItem to detect the current tier. No decision tree serialization, no state storage. Escalation group already present -> terminal `Fail`; otherwise -> `EscalateTo`.

### YAML case definitions in review/, wired in app/

Case definition YAML lives in `review/src/main/resources/devtown/`. Quarkus classloader picks it up from the review module JAR at test time. `PrReviewCaseHub` and `PrReviewCaseService` live in `app/`.

### Single source of truth for routing policy fields

threshold/minimumObservations/borderlineMargin come from `DevtownCapabilityRegistry`. Only blendFactor and quality floors are in YAML. This prevents the three base fields from existing in both places and diverging.

---

## Key Epics

1. Project scaffold (devtown#8)
2. Domain model -- capability tags, trust dimensions, routing thresholds (devtown#9)
3. PR review CasePlanModel -- content-driven routing and parallel checks (devtown#10)
4. Merge queue (casehub-refinery) -- batch-then-bisect
5. Cross-repo coordinated merge (devtown#156-#160)
6. Trust-weighted reviewer routing and post-merge feedback (devtown#57)
7. Failure handling -- DECLINED vs FAILED routing
8. GitHub integration
9. Notification wiring (devtown#16)
10. Observability and operational tooling
11. Case-Based Reasoning -- similarity-driven review routing and risk assessment (devtown#129)

---

## Current State

All six tutorial layers (1-6) are complete. Active development areas:
- SLA calibration enhancements (per-capability duration breakdown -- devtown#152)
- Coordinated change case definitions (devtown#169)
- Merge queue case definitions (devtown#168)
- Governance UI improvements (CasePlanModel browser, worker session management)
- Notification integration via SubscribableEvent (devtown#16, #166)

Open issues include: score decay for dormant contributors (devtown#179), per-domain contributor trust splitting (devtown#178), federated contributor trust (devtown#177), binding activation state visualization (devtown#170), case dependency graph (devtown#120), full trust visibility UI (devtown#98).

---

## Design Documents

| Document | What it covers |
|----------|---------------|
| `LAYER-LOG.md` | Authoritative layer-by-layer architecture record with key wiring, gotchas, and patterns to replicate |
| `docs/gastown-casehub-analysis-v2.md` | Full architectural comparison -- foundation vs foundation, application vs application |
| `docs/orchestration-advantages.md` | Seven concrete ACM advantages over workflow engines for PR review scenarios |
| `docs/DESIGN.md` | Trust-weighted selection strategy implementation detail |
| `docs/PROGRESS.md` | Improvement log with DT-NNN entries |
| `docs/specs/` | Design specs for each layer and epic |
