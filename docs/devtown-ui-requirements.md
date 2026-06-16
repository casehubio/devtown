# devtown UI Requirements

> **Date:** 2026-06-16
> **Platform:** `@casehub/ui` (melviz workbench — composable panels, YAML-driven layout, `@casehub/component` grid model)
> **Delivery:** Phased. Each phase is independently useful. Shared panels go to `@casehub/ui` commons; devtown-specific panels stay in devtown.

---

## Gastown UI Landscape

Gastown has no built-in UI. Two community projects fill the gap:

### gastown-gui (web3dev1337)

Node.js + Express + vanilla JS. Server-authoritative — all operations execute via `gt`/`bd` CLI subprocesses. WebSocket for real-time updates.

| Panel | What it shows |
|-------|-------------|
| Dashboard | Overview — rig count, agent count, system status |
| Rig list | Project repositories; spawn/stop/restart polecats; delete rigs |
| Work list | Bead tracking — create, view, sling work to agents |
| PR list | GitHub PRs across all rigs |
| Mail inbox | Messages from agents and polecats |
| Health | `gt doctor` results — system health checks |
| Crew management | Create, list, view persistent human workspaces |
| Formula operations | Create, list, apply workflow templates |

**Strength:** operational completeness — every `gt` CLI operation has a GUI equivalent. **Weakness:** no trust model, no compliance, no obligation lifecycle to visualise (because Gastown doesn't have them).

### gastown-viewer-intent (intent-solutions-io)

Go daemon + React/Vite + Bubbletea TUI. Three-tier (clients → gvid daemon → gt/bd adapters). SSE for real-time events. Single binary via `go:embed`.

| Panel | What it shows |
|-------|-------------|
| Board (Kanban) | Beads issues by status column; card navigation; detail drill-down |
| Graph | D3.js force-directed dependency visualisation; DOT export |
| Gas Town Dashboard | Molecules/wisps, convoys (Done/Active/Blocked counts), agent status (Active/Idle/Stuck), rig health |
| Memory panel | Read-only `bd memories` with redaction markers; substring search |
| Triage tab | Human-needed queue — beads carrying `human` label |
| Sync pill | Live Dolt sync state indicator |

**Strength:** observability depth — convoy progress, agent status detection, dependency graphs, memory inspection. **Weakness:** same as gastown-gui — nothing to show for trust, compliance, or obligation semantics.

---

## What devtown Has That Gastown Cannot Show

CaseHub's structural advantages create UI opportunities that have no Gastown equivalent:

| Capability | What a panel could show | Gastown equivalent |
|-----------|------------------------|-------------------|
| Trust scores | Per-agent per-capability trust score, phase (Bootstrap/Qualified/Borderline/Excluded), score trend over time | None — stamps are human-curated, not visualisable as a live metric |
| Commitment lifecycle | OPEN → ACKNOWLEDGED → FULFILLED/DECLINED/FAILED timeline per obligation | None — beads have status, not obligations |
| Routing decisions | Why agent A was selected over agent B — trust score, threshold, capability match, phase | None — GUPP is random selection with no rationale |
| Binding evaluation | Which bindings fired, which are waiting, what blackboard state satisfied each | None — formula steps are fixed, not reactive |
| Action oversight gates | Pending human approvals for consequential actions; classification category; approval/deny | None — agents execute autonomously |
| SLA timers | WorkItem deadline countdown; escalation stage; candidateGroup rotation | None — beads have timeout, no SLA lifecycle |
| Merkle audit chain | Causal chain per case: COMMAND → ACKNOWLEDGED → DONE → LedgerAttestation → trust update | None — Dolt history is admin-level, not case-level |
| Compliance report | EU AI Act Art.12 evidence per case across four regulatory dimensions | None |
| GDPR erasure status | Erasure receipts with chain integrity verification | None |

---

## Panel Taxonomy

### Shared panels (`@casehub/ui` commons)

Reusable by any CaseHub application (devtown, aml, clinical, life, etc.).

| Panel | Data source | Shared because |
|-------|------------|----------------|
| **WorkItem inbox** | `casehub-work` REST API | Every app has human tasks with SLA |
| **Commitment timeline** | `casehub-qhorus` REST API | Every app has agent obligations |
| **Trust score card** | `casehub-ledger` REST API + `TrustExportService` | Every app using trust routing |
| **Audit chain viewer** | `casehub-ledger` REST API | Every app has Merkle entries |
| **Case state** | `casehub-engine` REST API | Every app has cases |
| **Channel messages** | `casehub-qhorus` REST API + SSE | Every app has agent channels |

### devtown-specific panels

Domain logic visible only in devtown.

| Panel | Data source |
|-------|------------|
| **PR review case** | devtown `PrReviewResource` REST + case state |
| **Routing decision** | `TrustWeightedAgentStrategy` selection rationale (trust score, threshold, phase, capability) |
| **Action gate** | `ActionRiskClassifier` pending approvals — action type, classification, awaiting human |
| **Compliance report** | `CodeReviewComplianceService` — four regulatory dimensions per case |

---

## Phased Delivery

### Phase 1: Demo-ready — S · Low

The minimum to run the two demo paths (platform engineers + engineering managers) with a live UI instead of curl.

| Panel | Type | What it shows for demo |
|-------|------|----------------------|
| PR review timeline | devtown | Full lifecycle of one PR: opened → analysis → specialist triggered → agent selected → COMMAND → DONE → trust updated → merged. The demo story told visually. Uses engine EventLog + qhorus messages + ledger attestations. |
| Trust score card | Shared | Per-agent per-capability score; highlight routing selection — "Agent A (0.82) selected, Agent B (0.61) below threshold 0.70" |
| Routing explanation | devtown | Why agent A was selected: trust 0.82 > threshold 0.70, Phase 2 (Active), 14 observations. Agent B excluded: trust 0.61, below threshold. Uses TrustWeightedAgentStrategy rationale + RoutingPolicy + maturity phase. |
| Commitment timeline | Shared | COMMAND → ACKNOWLEDGED → FULFILLED sequence with timestamps |
| WorkItem inbox | Shared | Human review task with SLA countdown; escalation stage visible |

**Data:** REST polling initially (SSE in Phase 2). The demo runs a scripted scenario — polling at 2s intervals is sufficient.

**Layout:** Single page. PR review timeline across the top (full width). Trust score card + routing explanation side by side in the middle row. Commitment timeline + WorkItem inbox side by side at the bottom.

### Phase 2: Operational depth — M · Med

The panels that make devtown usable for day-to-day operation, not just demos.

| Panel | Type | What it adds |
|-------|------|-------------|
| Channel messages | Shared | Live SSE feed of COMMAND/RESPONSE/DONE/DECLINE per channel |
| Audit chain viewer | Shared | Merkle causal chain — click a case, see every entry with `causedByEntryId` links |
| Action gate | devtown | Pending ActionRiskClassifier approvals; approve/deny from the panel |
| Case kanban | Shared | Cases by status column (ACTIVE, WAITING, COMPLETED, FAULTED); card drill-down to case detail |
| Case memory browser | Shared | Read-only CaseMemoryStore entries per case — contributor history, reviewer context, code-area history |
| Worker session management | Shared | Spawn, stop, restart agent sessions; current session status; maps to claudony tmux lifecycle |
| Reviewer profile | devtown | Everything about one reviewer agent — trust per capability, active reviews, open obligations, capability health probe status, review history. Uses actor-state (assembles from ledger + work + qhorus + engine). |
| Review SLA dashboard | devtown | Reviews completed on time vs breached. Bottleneck identification — which capability or reviewer is the slowest. Uses WorkItem SLA data + breach history. |
| Risk classification summary | devtown | What consequential actions were gated, how many approved vs denied, which action types trigger most often. Uses ActionRiskClassifier decision history. |

**Data:** SSE for channel messages and case state updates. REST for audit queries and session management.

**Layout:** Tabbed or multi-page. "Live" tab (case kanban + channels + commitments). "Reviews" tab (reviewer profiles + SLA dashboard). "Trust" tab (scores + routing explanation). "Audit" tab (chain viewer + risk classification). "Workers" tab (sessions + memory).

### Phase 3: Parity with Gastown UIs + devtown domain depth — L · Med

What gastown-gui and gastown-viewer-intent provide that devtown doesn't yet, plus devtown-domain panels that leverage deeper platform capabilities.

| Panel | Type | Notes |
|-------|------|-------|
| Agent health | Shared | gastown-viewer-intent equivalent — Active/Idle/Stuck detection |
| Case dependency graph | Shared | gastown-viewer-intent equivalent — D3.js force-directed |
| Case definition browser | Shared | gastown-gui formula equivalent — view CasePlanModels, see bindings and goals, select for new cases |
| Fleet overview | Shared | gastown-gui dashboard equivalent — agent count, case count, system status |
| PR merge provenance | devtown | Visual PROV-DM lineage chain: who reviewed, who approved, trust scores at decision time, ComplianceSupplement attached. "Prove this merge was properly reviewed." No Gastown equivalent. |
| Compliance report | devtown | EU AI Act Art.12 evidence per case across four regulatory dimensions. No Gastown equivalent. |
| GDPR erasure | devtown | Erasure receipts with chain integrity verification. No Gastown equivalent. |
| Fleet review capacity | devtown | All reviewer agents, current load, trust phase per capability, availability. "Can we handle 10 PRs right now, or will we hit trust gaps on security-review?" Uses actor-state across all agents. |
| Operational tuning | devtown | Edit trust routing thresholds, SLA timers, risk classification levels from the UI. Scoped by `Path` (casehubio/devtown/trust-routing/security-review). Changes take effect immediately via PreferenceProvider. |
| PR tracking | devtown | gastown-gui PR list equivalent. Blocked on Epic #15 (GitHub integration). |
| Batch progress | devtown | gastown-viewer-intent convoy equivalent (Done/Active/Blocked counts). Blocked on Epic #11 (merge queue). |

### Phase 4: Beyond Gastown — M · Med

Panels that exploit CaseHub's structural advantages — things Gastown's UIs cannot show because the underlying model doesn't support them.

| Panel | Type | Why Gastown can't |
|-------|------|------------------|
| Review quality trends | devtown | Per-agent per-capability trust score trend over time. FLAGGED incidents marked on timeline. "Are reviewers getting better or worse?" | No trust model to trend |
| Maturity phase map | devtown | Per-capability per-agent phase (Bootstrap/Emerging/Active/Adaptive) as a heatmap | No maturity model |
| Obligation health | Shared | Fleet-wide obligation state — how many OPEN, ACKNOWLEDGED, stalled, delegated | No obligation concept |
| Binding evaluator | devtown | Live binding condition evaluation — which JQ predicates are satisfied, which are waiting, what blackboard state is missing | Fixed formula steps |

---

## REST API Surface for UI

The panels above need these endpoints. Most already exist; gaps noted.

| Endpoint | Owner | Status |
|----------|-------|--------|
| `GET /api/reviews/{prNumber}` | devtown | ✅ Exists (`PrReviewResource`) |
| `GET /api/reviews/{prNumber}/compliance` | devtown | ✅ Exists (`CodeReviewComplianceResource`) |
| `DELETE /api/reviews/erasure/{actorId}` | devtown | ✅ Exists (`DevtownErasureResource`) |
| `GET /api/workitems` | casehub-work | ✅ Exists |
| `GET /api/workitems/{id}` | casehub-work | ✅ Exists |
| `PUT /api/workitems/{id}/complete` | casehub-work | ✅ Exists |
| Qhorus channel + message REST | casehub-qhorus | ✅ Exists |
| Qhorus SSE events | casehub-qhorus | ✅ Exists |
| Trust score query (per actor, per capability) | casehub-ledger | ✅ Exists (`TrustExportService`) |
| Case state query | casehub-engine | ⚠️ Partial — `EventLog` queryable, no dedicated REST for binding state |
| Case list by status | casehub-engine | ⚠️ Gap — needed for kanban view; no list-by-status endpoint |
| Routing decision rationale | casehub-engine-ledger | ⚠️ Gap — selection decision not currently exposed via REST |
| Pending action gates | casehub-engine | ⚠️ Gap — `pendingActionGate` is in-memory only (engine#433) |
| Trust score history / trend | casehub-ledger | ⚠️ Gap — current scores queryable, history not exposed |
| CaseMemoryStore query | casehub-platform | ✅ Exists (`CaseMemoryStore.query()`) — needs REST exposure for UI |
| Worker session management | claudony | ✅ Exists (`SessionRegistry`, `TmuxService`) — REST endpoints exist |
| CasePlanModel listing | casehub-engine | ⚠️ Gap — no REST endpoint to list/inspect available case definitions |
| GitHub PR list | devtown | ❌ Blocked — requires Epic #15 (GitHub integration) |
| Batch/convoy progress | devtown | ❌ Blocked — requires Epic #11 (merge queue) |

---

## Relationship to claudony Dashboard

Claudony's existing dashboard provides terminal streaming (WebSocket), case worker panels, and channel message views. These are claudony-specific — tied to tmux session lifecycle and Claude CLI output.

The `@casehub/ui` panels are domain-level — they show case state, trust, obligations, and compliance. They complement the claudony dashboard, not replace it. When claudony ports to `@casehub/ui`, the terminal panel becomes a shared component alongside the panels defined here.
