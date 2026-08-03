# casehub-devtown -- Consumer Guide

> Software engineering coordination application built on the CaseHub agentic harness -- PR review accountability, trust-weighted routing, tamper-evident merge records, and cross-repo coordinated merges.

**GitHub:** [casehubio/devtown](https://github.com/casehubio/devtown)
**Tier:** Application

---

## What It Is

DevTown is a software engineering coordination application built on the CaseHub agentic harness. It coordinates specialist code reviewers (security, architecture, test coverage, performance), human review task gates with SLA, and adaptive PR routing based on code content -- producing a tamper-evident review record where every missed finding is traceable to a production incident.

DevTown also serves as the CaseHub field showcase and tutorial for Java developers. The codebase is structured as six progressive tutorial layers (Layers 1-6), each adding one foundation module and demonstrating its value. The code at every layer is production-grade. See `LAYER-LOG.md` in the project root for the authoritative layer-by-layer record.

This is the CaseHub answer to Gastown -- same domain (software engineering coordination), but built on the domain-agnostic foundation rather than baked into infrastructure. See `docs/gastown-casehub-analysis-v2.md` for the full architectural comparison.

---

## Module Structure

Six Maven modules, ordered by dependency:

| Module | Package root | Responsibility |
|--------|-------------|----------------|
| `domain` | `io.casehub.devtown.domain` | Pure Java domain model -- zero framework imports, zero JPA. Capability tags, trust dimensions, routing policies, CBR similarity model, SLA types, preference keys |
| `review` | `io.casehub.devtown.review` | Port interfaces, case definition YAML, CBR retrieval SPI, compliance types, notification events, SLA calibration store SPI. Depends on domain; no Quarkus |
| `queue` | `io.casehub.devtown.queue` | Merge queue domain -- batch composition, priority calculation, bisection strategies, risk assessment. Pure Java; no Quarkus |
| `merge` | `io.casehub.devtown.merge` | Merge queue port interfaces -- `MergeQueuePort`, `MergeQueueStore`, queue entry/batch records. Pure Java |
| `github` | `io.casehub.devtown.github` | GitHub integration -- webhook handler, CI status client, merge client, revert client, batch branch management, payload mapping, signature verification |
| `app` | `io.casehub.devtown.app` | Quarkus runtime assembly -- all CDI wiring, REST resources, MCP tools, persistence adapters, notification bridges, trust routing, governance workbench |

---

## Tutorial Layers

The tutorial structure emerges from the natural adoption sequence. Each layer adds one foundation module and makes its value tangible relative to the previous layer.

`LAYER-LOG.md` in the project root is the authoritative layer-by-layer record with cross-references, key wiring, and gotchas. Update it when a layer completes or makes significant progress.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Naive Java -- no CaseHub | Baseline: direct service calls to analysis agents, no accountability | complete |
| 2 | casehub-work | No formal SLA for reviewer response; reviewer assignments not tracked | complete |
| 3 | casehub-qhorus | No formal obligation per specialist reviewer; DECLINE when outside expertise | complete |
| 4 | casehub-ledger | No tamper-evident review record; cannot trace production incident to missed finding | complete |
| 5 | casehub-engine | Fixed review pipeline; no adaptive routing on security flags or architecture changes | complete |
| 6 | Trust routing | No trust model; experienced security reviewers not prioritised on sensitive PRs | complete |

---

## Capability Vocabulary

DevTown's capability tags are split into four typed classes with distinct routing semantics (not a flat string list as in Gastown). This split determines which capabilities are trust-scored, which are operational, and which require human judgment.

### ReviewDomain -- analytical review capabilities (trust-scored on quality)

| Constant | String value |
|----------|-------------|
| `CODE_ANALYSIS` | `code-analysis` |
| `SECURITY_REVIEW` | `security-review` |
| `ARCHITECTURE_REVIEW` | `architecture-review` |
| `STYLE_REVIEW` | `style-review` |
| `TEST_COVERAGE` | `test-coverage` |
| `PERFORMANCE_ANALYSIS` | `performance-analysis` |

### AgentQualification -- execution capabilities (trust-scored on outcome)

| Constant | String value |
|----------|-------------|
| `CI_RUNNER` | `ci-runner` |
| `MERGE_EXECUTOR` | `merge-executor` |
| `COORDINATED_MERGE` | `coordinated-merge` |
| `COORDINATED_ROLLBACK` | `coordinated-rollback` |

### HumanDecision -- human decisions (with SLA and lifecycle)

| Constant | String value |
|----------|-------------|
| `PR_APPROVAL` | `human-decision:pr-approval` |

### HumanOversight -- system-triggered oversight (on routing uncertainty)

| Constant | String value |
|----------|-------------|
| `ROUTING_REVIEW` | `human-oversight:routing-review` |
| `GENERAL` | `human-oversight:general` |

### MergeQueueCapability -- merge queue operations

| Constant | String value |
|----------|-------------|
| `BATCH_CI_RUNNER` | `batch-ci-runner` |
| `BISECTION_SPLITTER` | `bisection-splitter` |
| `PR_REJECT_AND_NOTIFY` | `pr-reject-and-notify` |
| `MERGE_QUEUE_ENQUEUE` | `merge-queue-enqueue` |

All 14 capabilities are registered in `DevtownCapabilityRegistry`.

---

## Trust Dimensions

`DevtownTrustDimension` defines four quality dimensions for trust scoring:

| Dimension | String value | Meaning |
|-----------|-------------|---------|
| `REVIEW_THOROUGHNESS` | `review-thoroughness` | Does the agent find issues that later cause incidents? |
| `PRECISION` | `precision` | TP/(TP+FP) -- higher is better. Does the agent flag issues that turn out to be non-issues? |
| `SCOPE_CALIBRATION` | `scope-calibration` | Does the agent correctly DECLINE work outside its capability? |
| `RESPONSIVENESS` | `responsiveness` | How quickly does the agent respond to assignments? |

---

## Routing Thresholds

Per-capability routing policies from `DevtownCapabilityRegistry.POLICIES`:

| Capability | Threshold | Min observations | Borderline margin | Fallback |
|-----------|-----------|-----------------|-------------------|----------|
| `security-review` | 0.70 | 10 | 0.05 | `human-oversight:routing-review` |
| `architecture-review` | 0.65 | 8 | 0.05 | `human-oversight:routing-review` |
| `style-review` | 0.50 | 5 | -- | -- |
| `merge-executor` | 0.80 | 15 | 0.05 | `human-oversight:routing-review` |

Capabilities without explicit policies use engine defaults.

---

## CasePlanModel Definitions

DevTown defines three YAML CasePlanModels loaded by the engine at startup.

### PR Review Case (`review/src/main/resources/devtown/pr-review.yaml`)

The primary case definition. Content-driven routing fires capabilities based on code analysis findings -- security review fires only when security-sensitive code is detected, not from author labels. Goals, bindings, and capabilities are declared in YAML; the engine drives coordination.

- 9 bindings across 4 groups
- 3 goals
- 9 capabilities
- Trust-weighted selection strategy for reviewer assignment (`cbrWeight` on `TrustRoutingPolicy`)
- Post-merge trust feedback via FLAGGED attestation when production incident traced to missed review
- Human approval binding via `HumanTaskTarget.inline()` with SLA

`PrReviewCaseDefinition` (in `review/src/main/`) provides a fluent DSL factory with `LambdaExpressionEvaluator` for binding conditions. `PrReviewCaseDefinitionEquivalenceTest` verifies structural parity between the DSL and YAML.

### Merge Batch Case (`merge/src/main/resources/devtown/merge-batch.yaml`)

Batch lifecycle case for merge queue -- batch formation, CI run, bisection on failure, merge execution.

### Merge Queue Entry Case (`merge/src/main/resources/devtown/merge-queue-entry.yaml`)

Per-PR lifecycle within the merge queue -- admission, queueing, batch assignment, completion/rejection.

### Coordinated Change Case (`app/src/main/resources/casehub/devtown/coordinated-change.yaml`)

Cross-repo coordinated merge -- parent case spawning per-repo sub-cases with automatic rollback on fault.

---

## RBAC Roles

`DevtownRoles` defines a 5-role model:

| Role | Claim string | Used by |
|------|-------------|---------|
| `ADMIN` | `devtown-admin` | `GovernanceResource`, `IncidentFeedbackResource`, `GdprErasureResource`, `MemoryAdminResource`, `CodeReviewComplianceResource` |
| `ENGINEER` | `devtown-engineer` | `PrReviewResource`, `CodeReviewComplianceResource` |
| `AUDITOR` | `devtown-auditor` | `CodeReviewComplianceResource` |
| `DATA_CONTROLLER` | `devtown-data-controller` | `GdprErasureResource` |
| `SERVICE` | `devtown-service` | `PrReviewResource` |

---

## REST API

### PR Review

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/reviews` | ADMIN, ENGINEER, SERVICE | Start a PR review case. Accepts `PrPayload` (repo, prNumber, headSha, baseRef, linesChanged, contributor, changedPaths) |

### Governance Workbench

All governance endpoints are under `/api/governance` and currently `@PermitAll`:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/governance/queue-status` | PR review queue status -- counts by status, active reviews |
| `GET` | `/api/governance/recent-events` | Recent events from ring buffer (query params: `limit`, `since`) |
| `GET` | `/api/governance/system-health` | System health metrics across all cases and agents |
| `GET` | `/api/governance/problems` | Detected problems: stalled cases, expired commitments, failed workers, SLA breaches. Cursor-based pagination |
| `GET` | `/api/governance/reviews` | Review list with cursor-based pagination |
| `GET` | `/api/governance/reviews/{caseId}` | Detailed review status including timeline and capability progress |
| `GET` | `/api/governance/reviewers` | Reviewer fleet -- trust scores by capability. Cursor-based pagination |
| `GET` | `/api/governance/reviewers/{actorId}` | Individual reviewer health: commitments, trust scores, decision history |
| `GET` | `/api/governance/merge-queue` | Merge queue state: queued PRs, active batches |
| `GET` | `/api/governance/merge-queue/metrics` | Operational metrics: queue depth, wait times, throughput, failure rate, trust scores |
| `GET` | `/api/governance/merge-queue/batch/{batchId}` | Batch state: PRs in batch, risk level, bisection strategy |
| `GET` | `/api/governance/triage` | Triage items: incidents, FLAGGED attestations. Cursor-based pagination |

### Compliance

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/compliance/code-review/{caseId}` | ADMIN, ENGINEER, AUDITOR | Compliance evidence for a case: audit chain, review SLA, trust routing, GDPR |

### Incident Feedback

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/incident-feedback` | ADMIN | Record production incident against merged PR -- writes FLAGGED attestation |

### GDPR Erasure

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/actors/{actorId}/erasure` | ADMIN, DATA_CONTROLLER | GDPR Art.17 erasure: pseudonymises actor identity in ledger, cleans `CaseMemoryStore`, persists tamper-evident `ErasureReceiptLedgerEntry` |

### Memory Admin

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/admin/memory/erase/contributor` | ADMIN | GDPR erasure of contributor memory by login |

### GitHub Webhook

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/github/webhook` | GitHub webhook handler -- routes `pull_request`, `check_suite`, `check_run` events to case lifecycle (`revisePr`, `closePr`, `signalCiStatus`, `signalCheckRun`) |

---

## MCP Tools

`DevtownMcpTools` exposes 22 MCP tools via `quarkus-mcp-server`:

### Read Tools

| Tool | Purpose |
|------|---------|
| `get_queue_status` | Current PR review queue status with counts by status and active reviews |
| `get_recent_events` | Recent PR review events from ring buffer (params: limit, since) |
| `get_system_health` | Overall system health metrics across all cases and agents |
| `list_problems` | Stalled cases, expired commitments, failed workers, queue SLA breaches (param: threshold_minutes) |
| `inspect_review` | Detailed review status including timeline and capability progress (param: case_id) |
| `get_reviewer_health` | Reviewer commitments, trust scores, decision history (param: reviewer_id) |
| `get_prior_decisions` | Prior review decisions for a repository and file path -- uses `CaseMemoryStore` |
| `search_memory_by_contributor` | Contributor's review history: outcomes, patterns, prior decisions |
| `search_memory_by_capability` | All entries related to a specific review capability across contributors |
| `export_prov` | PROV-DM provenance record for a case (PROV-JSON-LD format) |
| `get_merge_queue` | Current merge queue state: queued PRs with priority scores, wait times, dependencies, active batches |
| `get_batch_status` | Batch state: PRs in batch, risk level, bisection strategy |
| `get_merge_queue_metrics` | Operational metrics: queue depth, active batches, wait times, throughput, failure rate, trust score, lane and batch size distribution |
| `get_failure_rates_by_repository` | Per-repository batch failure rates from completed merge queue batches |
| `evaluate_failure_rate_alerts` | Check per-repository failure rates against thresholds and fire alerts |
| `get_evidential_violations` | Evidential benchmark violations from FLAGGED attestations (optional: commitmentId filter) |
| `find_similar_cases` | CBR similarity search -- ranked precedents with scores and capability outcomes |
| `get_cbr_weight_status` | Current CBR similarity weights and dynamic adjustments from outcome feedback |
| `get_agent_messages` | Agent channel message history for a case: dispatch, completion, decline, failure events |

### Write Tools

| Tool | Purpose |
|------|---------|
| `retry_reviewer` | Retry a specific capability by signaling the case with a null context value |
| `reroute_review` | Cancel current case and start a fresh review with the same PR payload |
| `force_complete_check` | Force-complete a capability check with operator override (APPROVED/DECLINED + reason) |
| `enqueue_pr` | Add a PR to the merge queue with priority lane and trust score |
| `dequeue_pr` | Remove a PR from the merge queue |
| `supersede_pr` | Supersede a PR case -- marks old case SUPERSEDED, opens replacement, links with audit trail |
| `report_incident` | Report production incident against merged PR -- writes FLAGGED attestation against reviewer trust score |

---

## Merge Queue

Full merge queue lifecycle across `queue/`, `merge/`, and `app/` modules.

### Batch Composition

`DefaultBatchCompositionPolicy` handles priority scoring via `QueuePriorityCalculator`:

```
priority = lane_weight * 1000 + trust * 100 + wait_decay
```

Risk-aware grouping with adaptive sizing:

```
max_batch = max(minBatchSize, floor(minTrust * maxBatchSize * (1 - recentFailureRate)))
```

### Priority Lanes

`PriorityLane` enum with three levels: `NORMAL` (weight 1), `HIGH` (weight 2), `CRITICAL` (weight 3).

### SLA

Per-priority-lane SLAs: `CRITICAL` = PT1H, `HIGH` = PT4H, `NORMAL` = PT8H. `MergeQueueSlaBreachObserver` observes breaches. `DefaultSlaBreachPolicy` handles escalation.

### Bisection Strategies

Four strategies implementing `BisectionSplitStrategy`:

| Strategy | Approach |
|----------|----------|
| `BinarySplitStrategy` | Simple binary split |
| `PrecedentBisectionStrategy` | Risk-score-sorted, high-risk candidates in left slice |
| `TrustWeightedSplitStrategy` | Trust-weighted split |
| `IsolateOutlierStrategy` | Outlier isolation |

### Batch Risk Assessment

`CbrBatchRiskAssessor` implements `BatchRiskAssessor` -- CBR-based batch risk scorer using precedent lookup from `CaseMemoryStore`.

### Persistence

`JpaMergeQueueStore` with `BatchEntity` and `QueuedPrEntity`. `MergeQueuePort` hexagonal port interface in the `merge` module.

### Batch Retention

`BatchRetentionJob` (`@Scheduled(cron = "0 0 3 * * ?")`) with configurable retention days (default 30).

### Batch Branch Management

`GitHubBatchBranchClient` in the `github` module handles git operations for merge queue batch testing -- creating batch branches, checking merge status.

---

## Cross-Repo Coordinated Merge

`CoordinatedChangeService` implements `CoordinatedChangePort` for cross-repo coordinated merges:

1. Validates no active review exists for any target PR
2. Starts a parent coordinated-change case (`coordinated-change.yaml`)
3. Starts per-repo sub-cases via `PrReviewApplicationService.startReview()` with `coordinatedChange: true`
4. Tracks parent-to-sub-case relationships via `CoordinatedChangeTracker`
5. Signals parent case with started review case IDs

`CoordinatedChangeRequest` carries a list of `RepoChangeEntry` (owner, repo, prNumber, headSha, targetBranch, contributor, changedPaths, linesChanged).

Workers: `CoordinatedMergeWorker` merges all repos when sub-cases complete; `CoordinatedRollbackWorker` reverts merges on sub-case FAULT via `RevertClient`. `CoordinatedChangeTrackerHydrator` handles startup hydration from durable state.

---

## CBR (Case-Based Reasoning)

### PR Similarity Model

`PrFeatureVector` -- structured feature extraction from PRs (file paths, modules, languages, change size, contributor). `WeightedJaccardSimilarity` -- 5-dimension weighted scoring with per-dimension breakdown.

### Retrieval

`CbrRetrievalService` interface with `DefaultCbrRetrievalService` implementation -- scans case-vector memories, applies `SimilarityGate` hard gates (minimum overlap filters before scoring), scores with `WeightedJaccardSimilarity`, enriches with `CapabilityOutcome` data, computes completion times.

### Precedent Activation

`PrecedentActivationPolicy` fires review capabilities based on precedent similarity. Per-capability `ActivationThreshold` values. `CbrWeightAdjuster` handles dynamic weight adjustment from outcome feedback.

### CBR-Enhanced Reviewer Matching

CBR integrated into reviewer selection via `cbrWeight` on `TrustRoutingPolicy`. `DevtownTrustRoutingPolicyProvider` provides per-capability CBR weights (defaults: security-review=0.2, architecture-review=0.2, style-review=0.2). Engine-ledger's `TrustWeightedAgentStrategy` uses `AgentRoutingContext.experiences()` to apply CBR bonus -- an agent with lower trust but higher precedent match can win over a higher-trust agent with no precedent.

### Configuration

`CbrPreferenceKeys` -- K_LIMIT, MIN_THRESHOLD, TIME_WINDOW_DAYS, per-dimension weights.

---

## SLA Calibration

`SlaEstimator` computes SLA estimates from similar past review assignments using CBR precedents. Returns `SlaEstimate` with `DurationStats` (median, min, max, sampleCount) overall and per-capability breakdown.

`SlaCalibrationStore` persists calibration records (`SlaCalibrationRecord`) via `JpaSlaCalibrationStore` with `SlaCalibrationEntity` JPA entity.

`SlaEstimate.toContextMap()` injects calibration data into case context (medianSeconds, minSeconds, maxSeconds, sampleCount, capabilityBreakdown).

SLA override preference keys allow calibrated estimates to replace configured SLAs.

---

## CaseMemoryStore Integration

Contributor history, reviewer agent context, and code-area history injected before PR review case starts; review outcomes written to memory at case close.

- `MemoryContext` aggregates contributorHistory, codeAreaHistory, precedents, and precedentActivations with `hasRiskSignals()` for risk detection
- `CaseMemoryRecaller` -- called before `PrReviewCaseService.startCase()` to inject memory context
- `CaseMemoryEmitter` -- stores review outcomes at case close via `ReviewOutcomeObserver`
- `FeatureVectorEmitter` -- stores case-scoped feature vectors as memory facts at case open for CBR retrieval

Memory domains and keys in `domain/memory/`: `DevtownMemoryDomain` (domain constants, CONTRIBUTOR_PREFIX, MODULE_PREFIX), `DevtownMemoryKeys`, `MemoryRecallKeys`, `ModulePathNormalizer`.

---

## Notification Events

Seven `SubscribableEvent` types in `review/notification/`:

| Event | Type string | Meaning |
|-------|------------|---------|
| `AgentReviewDispatchedEvent` | `io.casehub.devtown.review.agent-dispatched` | Agent dispatched for capability review |
| `ReviewAssignedEvent` | `io.casehub.devtown.review.assigned` | PR assigned for human review |
| `MergeSucceededEvent` | `io.casehub.devtown.merge.succeeded` | Batch merge completed successfully |
| `MergeFailedEvent` | `io.casehub.devtown.merge.failed` | Merge rejected (CI failure) |
| `SlaEscalatedEvent` | `io.casehub.devtown.sla.escalated` | SLA breach -- escalated |
| `StalledCommitmentEvent` | `io.casehub.devtown.commitment.stalled` | Stalled commitment detected by watchdog |
| `CaseFaultedEvent` | `io.casehub.devtown.case.faulted` | Case entered fault state |

`DevtownSubscriptionRegistrar` registers seven system-scope subscriptions at startup via `SubscriptionStore`, routing to notification targets (event fields, groups, entity watchers) with severity-based templates.

Five notification bridge classes in `app/notification/` translate platform events to devtown-specific `SubscribableEvent` types:
- `AgentDispatchNotificationBridge`
- `ReviewAssignmentNotificationBridge`
- `CaseLifecycleNotificationBridge`
- `SlaBreachNotificationBridge`
- `WatchdogAlertNotificationBridge`

---

## Compliance

`CodeReviewComplianceService` produces `CodeReviewComplianceEvidence` for a case, covering:
- `AuditChainRequirement` -- tamper-evident ledger chain integrity
- `ReviewSlaRequirement` -- SLA compliance for review assignments
- `TrustRoutingRequirement` -- trust-weighted routing compliance
- `GdprRequirement` -- GDPR erasure state
- `InclusionProofRecord` -- Merkle inclusion proof from ledger

`CodeReviewComplianceResource` serves evidence at `GET /api/compliance/code-review/{caseId}`.

---

## EvidentialChecker Integration

`EvidentialAttestationPolicy` (`@Alternative @Priority(2)`) consumes `EvidentialChecker` from `io.casehub.qhorus.runtime.audit`. Runs all four benchmark variants: V1 (artefact check), V2 (channel check), V3 (correlation check), V4 (token check with content). Checks run only for configured phases per capability. `EvidentialViolationStore` stores violation records. MCP tool `get_evidential_violations` lists violations from FLAGGED attestations.

---

## Action Risk Classifier

`DevtownActionRiskClassifier` implements engine's `ActionRiskClassifier` SPI. Eight action types with four classification categories:

| Action type | Constant |
|-------------|----------|
| `pr-merge-execute` | `PR_MERGE_EXECUTE` |
| `pr-force-merge` | `PR_FORCE_MERGE` |
| `pr-review-override` | `PR_REVIEW_OVERRIDE` |
| `security-escalation` | `SECURITY_ESCALATION` |
| `issue-close-invalid` | `ISSUE_CLOSE_INVALID` |
| `dependency-removal` | `DEPENDENCY_REMOVAL` |
| `contributor-access-change` | `CONTRIBUTOR_ACCESS_CHANGE` |
| `production-deploy` | `PRODUCTION_DEPLOY` |

`DevtownRiskClassifierProducer` (`@RiskClassifier @ApplicationScoped`) is the CDI adapter. PreferenceProvider-driven thresholds at scope `casehubio/devtown/risk/<actionType>`. Gate operates through engine's `ActionGateWorkItemHandler` lifecycle.

---

## GitHub Integration

The `github` module provides GitHub-specific adapters:

| Class | Implements | Purpose |
|-------|-----------|---------|
| `GitHubWebhookResource` | -- | `POST /api/github/webhook` -- routes `pull_request`, `check_suite`, `check_run` events; HMAC signature verification via `GitHubSignatureVerifier` |
| `GitHubCiStatusClient` | `CiStatusClient` | Checks CI status via GitHub Checks API |
| `GitHubMergeClient` | `MergeClient` | Merges PRs via GitHub merge API |
| `GitHubRevertClient` | `RevertClient` | Reverts merges via PR-based revert for coordinated rollback |
| `GitHubBatchBranchClient` | `BatchBranchClient` | Git operations for merge queue batch branch management |
| `GitHubPayloadMapper` | -- | Maps GitHub webhook payloads to domain `PrPayload` |

REST client interfaces: `GitHubPullRequestApi`, `GitHubMergeApi`, `GitHubChecksApi`, `GitHubGitApi`.

---

## Governance Workbench UI

Web UI built with casehub-pages DSL. Six views:

- **Operations** -- operational metrics dashboard (throughput, latency, error rates)
- **Reviews** -- PR review case workbench with inbox and detail tabs
- **Merge Queue** -- merge batch status, SLA tracking, batch composition
- **Reviewers** -- reviewer trust management: list view with trust scores (by capability), profile view with trust charts and decision history
- **Triage** -- incident feedback and FLAGGED attestation entry for production incidents traced to missed reviews
- **System** -- configuration, diagnostics, health checks

Additional UI features:
- **CasePlanModel browser** (devtown#119) -- Definitions tab and live case state
- **Worker session management** (devtown#123) -- spawn, stop, restart agent sessions

`GovernanceQueryService` aggregates reviewer health metrics from ledger, trust scores, and commitment state.

`GovernanceEventBridge` (`@ServerEndpoint("/governance/events")`) -- WebSocket endpoint for real-time reviewer status updates.

Cursor-based pagination via `PagedResult<T>` with Base64-encoded offset cursors, configurable limit (max 200).

---

## PR Lifecycle

`PrReviewApplicationService` is the central port interface with five lifecycle operations:

| Method | Purpose |
|--------|---------|
| `startReview(PrPayload)` | Open a new PR review case |
| `startReview(PrPayload, Map additionalContext)` | Open with extra context (e.g. `coordinatedChange: true`) |
| `revisePr(repo, prNumber, newHeadSha, linesChanged)` | PR updated -- new commits pushed |
| `closePr(repo, prNumber, merged)` | PR closed or merged |
| `signalCiStatus(repo, prNumber, headSha, suiteId, conclusion)` | CI check suite completed |
| `signalCheckRun(repo, prNumber, headSha, checkName, conclusion, completedAt)` | Individual check run completed |
| `supersedePr(repo, oldPrNumber, replacement)` | Supersede a PR case with a replacement |

---

## Dependencies

```
casehub-devtown
  -> casehub-engine              (CasePlanModel, sub-cases, bindings)
  -> casehub-engine-ledger       (TrustWeightedAgentStrategy, CBR cbrWeight routing)
  -> casehub-ledger              (Merkle audit, trust scoring, GDPR erasure)
  -> casehub-work                (human review WorkItem, SLA, escalation)
  -> casehub-qhorus              (COMMAND/RESPONSE per reviewer, commitment lifecycle, EvidentialChecker)
  -> casehub-connectors          (Slack/Teams for review assignments and failures)
  -> casehub-platform-memory-inmem (in-memory CaseMemoryStore for @QuarkusTest isolation)
  -> casehub-platform-oidc       (OidcCurrentPrincipal, @RolesAllowed enforcement)
  -> casehub-work-engine-adapter (ActionRiskClassifier oversight gate)
  -> casehub-platform-config     (PreferenceProvider for routing/trust/risk preferences)
  -> casehub-neocortex           (CaseMemoryStore for CBR and contributor history)
  -> casehub-blocks              (TrustRoutingRequirement for compliance)
```

---

## What It Does NOT Own

Everything below belongs in the foundation:

- Trust scoring computation (casehub-ledger)
- Commitment lifecycle and obligation tracking (casehub-qhorus)
- Case engine, blackboard, and CasePlanModel runtime (casehub-engine)
- WorkItem inbox and human task lifecycle (casehub-work)
- Notification delivery (casehub-connectors)
- CaseMemoryStore implementation (casehub-neocortex / casehub-platform-memory-inmem)
- PROV-DM export (casehub-ledger)
- PreferenceProvider (casehub-platform-config)
