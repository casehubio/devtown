# casehub-devtown — Consumer Guide

> Software engineering coordination application built on the CaseHub agentic harness — PR review accountability, trust-weighted routing, and tamper-evident merge records.

**GitHub:** [casehubio/devtown](https://github.com/casehubio/devtown)
**Tier:** Application

---

## What It Is

A software engineering coordination application built on the CaseHub agentic harness. Coordinates specialist code reviewers (security, architecture, test coverage), human review task gates with SLA, and adaptive PR routing based on code content — producing a tamper-evident review record where every missed finding is traceable. Field showcase and tutorial for Java developers in software engineering and DevOps.

This is the CaseHub answer to Gastown — same domain (software engineering coordination), but built on the domain-agnostic foundation rather than baked into infrastructure. See `docs/gastown-casehub-analysis-v2.md` in this repo for the full architectural comparison.

## Tutorial Layers

The tutorial structure emerges from the natural adoption sequence. Each layer adds one foundation module and makes its value tangible relative to the previous layer. The code at every layer is production-grade.

LAYER-LOG.md in the project root is the authoritative layer-by-layer record with cross-references, key wiring, and gotchas. Update it when a layer completes or makes significant progress.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Naive Java — no CaseHub | Baseline: direct service calls to analysis agents, no accountability | complete |
| 2 | casehub-work | No formal SLA for reviewer response; reviewer assignments not tracked | code complete (LAYER-LOG entry pending engine#326) |
| 3 | casehub-qhorus | No formal obligation per specialist reviewer; DECLINE when outside expertise | complete |
| 4 | casehub-ledger | No tamper-evident review record; cannot trace production incident to missed finding | complete |
| 5 | casehub-engine | Fixed review pipeline; no adaptive routing on security flags or architecture changes | complete |
| 6 | Trust routing | No trust model; experienced security reviewers not prioritised on sensitive PRs | complete |
| 7 | Comparison vs naive AI code review | — | pending |

## What It Owns

### Capability Tags

Capability tag definitions for the software development domain: `code-analysis`, `security-review`, `architecture-review`, `style-review`, `test-coverage`, `merge-executor`, etc.

### Trust Dimensions

- `review-thoroughness` — does the agent find issues that later cause incidents?
- `false-positive-rate` — does the agent flag issues that turn out to be non-issues?
- `scope-calibration` — does the agent correctly DECLINE work outside its capability?

### Routing Thresholds

Routing thresholds per capability:

| Capability | Threshold | Min observations | Borderline margin |
|-----------|-----------|-----------------|-------------------|
| `security-review` | 0.70 | 10 | 0.05 |
| `architecture-review` | 0.65 | 8 | 0.05 |
| `style-review` | 0.50 | 5 | — |
| `merge-executor` | 0.80 | 15 | 0.05 |

### CasePlanModel Definitions

**PR Review Case** — goals, bindings, content-driven routing from code analysis findings. Trust-weighted selection strategy for code review domain. Post-merge trust feedback — FLAGGED attestation when production incident traced to missed review.

**Merge Queue Case** (casehub-refinery) — batch-then-bisect strategy as binding conditions. Cross-repo coordinated merge — parent case + per-repo sub-cases with automatic rollback on fault.

### Roles

`DevtownRoles` — 5-role RBAC model:

| Role | Claim | Used by |
|------|-------|---------|
| `ADMIN` | `devtown-admin` | `GovernanceResource`, `IncidentFeedbackResource`, `GdprErasureResource`, `MemoryAdminResource` |
| `ENGINEER` | `devtown-engineer` | `GovernanceResource` |
| `AUDITOR` | `devtown-auditor` | `GovernanceResource` |
| `DATA_CONTROLLER` | `devtown-data-controller` | `GdprErasureResource` |
| `SERVICE` | `devtown-service` | — |

### MCP Tools

| Tool | Purpose |
|------|---------|
| `get_prior_decisions` | Find prior review decisions for a repository and file path |
| `search_memory_by_contributor` | Search case memory for a contributor's review history |
| `search_memory_by_capability` | Search case memory for entries related to a review capability |
| `report_incident` | Report production incident against merged PR — writes FLAGGED attestation |
| `get_evidential_violations` | List evidential benchmark violations from FLAGGED attestations |
| `find_similar_cases` | CBR similarity search — ranked precedents with scores and capability outcomes |
| `get_cbr_weight_status` | Show current CBR similarity weights and dynamic adjustments |
| `get_agent_messages` | Agent channel message history for a case — dispatch, completion, decline, failure events |

### GDPR Erasure

`POST /api/actors/{actorId}/erasure` — GDPR Art.17 erasure: pseudonymises actor identity in ledger, cleans `CaseMemoryStore` (`contributor:` + `reviewer:` prefixes), persists tamper-evident `ErasureReceiptLedgerEntry`. SHA-256 hash fallback when no `ActorIdentity` mapping exists.

### Incident Feedback

`POST /api/incident-feedback` — records FLAGGED attestations against agents whose PR reviews missed issues found in production incidents. Idempotent via `findAttestationsByAttestorIdAndCapabilityTag` (tokenisation-proof).

## Merge Queue

Full merge queue lifecycle in `queue/`, `merge/`, and `app/` modules.

**Batch composition** (`DefaultBatchCompositionPolicy`): priority scoring via `QueuePriorityCalculator` (lane weight * 1000 + trust * 100 + wait decay), risk-aware grouping, adaptive sizing. Adaptive max: `max(minBatchSize, floor(minTrust * maxBatchSize * (1 - recentFailureRate)))`.

**Bisection strategies**: `BinarySplitStrategy` (simple binary split), `PrecedentBisectionStrategy` (risk-score-sorted, high-risk in left slice), `TrustWeightedSplitStrategy` (trust-weighted split), `IsolateOutlierStrategy` (outlier isolation). All implement `BisectionSplitStrategy` interface.

**Precedent-based routing** (`CbrBatchRiskAssessor`): CBR-based batch risk scorer using precedent lookup from `CaseMemoryStore`. Implements `BatchRiskAssessor` interface.

**SLA**: per-priority-lane SLAs — `SLA_CRITICAL` = PT1H, `SLA_HIGH` = PT4H, `SLA_NORMAL` = PT8H. `MergeQueueSlaBreachObserver` observes breaches. `DefaultSlaBreachPolicy` handles escalation.

**Persistence**: `JpaMergeQueueStore` with `BatchEntity` and `QueuedPrEntity`. `MergeQueuePort` hexagonal port interface.

**Batch retention**: `BatchRetentionJob` (`@Scheduled(cron = "0 0 3 * * ?")`) with configurable retention days (default 30).

## Governance Workbench UI

Web UI built with casehub-pages DSL. Six views:

- **Operations** — operational metrics dashboard (throughput, latency, error rates)
- **Reviews** — PR review case workbench with inbox and detail tabs
- **Merge Queue** — merge batch status, SLA tracking, batch composition
- **Reviewers** — reviewer trust management: list view with trust scores (by capability), profile view with trust charts and decision history
- **Triage** — incident feedback and FLAGGED attestation entry for production incidents traced to missed reviews
- **System** — configuration, diagnostics, health checks

**GovernanceQueryService** aggregates reviewer health metrics from ledger, trust scores, and commitment state.

**GovernanceEventBridge** (`@ServerEndpoint("/governance/events")`) — WebSocket endpoint for real-time reviewer status updates.

## Dependencies

```
casehub-devtown
  → casehub-engine   (CasePlanModel, sub-cases, bindings)
  → casehub-engine-ledger           (TrustWeightedAgentStrategy, CBR cbrWeight routing)
  → casehub-ledger   (Merkle audit, trust scoring, GDPR)
  → casehub-work     (human review WorkItem, SLA, escalation)
  → casehub-qhorus   (COMMAND/RESPONSE per reviewer, commitment lifecycle, EvidentialChecker)
  → casehub-connectors (Slack/Teams for review assignments and failures)
  → casehub-platform-memory-inmem  (in-memory CaseMemoryStore for @QuarkusTest isolation)
  → casehub-platform-oidc          (OidcCurrentPrincipal, @RolesAllowed enforcement)
  → casehub-engine-work-adapter    (ActionRiskClassifier oversight gate)
```

## What It Does NOT Own

Everything below belongs in the foundation:

- Trust scoring computation (casehub-ledger)
- Commitment lifecycle (casehub-qhorus)
- Case engine and blackboard (casehub-engine)
- WorkItem inbox (casehub-work)
- Notification delivery (casehub-connectors)
