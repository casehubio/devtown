# CaseHub vs Gastown: Architectural Analysis v4

> **Date:** 2026-06-26
> **Previous versions:** [v3](gastown-casehub-analysis-v3.md) (2026-06-18), [v2](gastown-casehub-analysis-v2.md) (2026-05-25), [v1](gastown-casehub-analysis.md) (2026-04-27, archived — uses obsolete naming)

---

## 1. Executive Summary

Six foundation layers plus operational tooling have shipped in `casehub-devtown`. The closed feedback loop — where trust scores derived from cryptographically attested commitment outcomes drive future routing without human intervention — is live. CaseHub surpasses Gastown on model quality, compliance, trust, governance, and AI-native operations. Gastown leads on operational maturity and recovery automation.

The central architectural difference remains **layering**. CaseHub is a domain-agnostic coordination foundation with domain applications on top. Gastown is a software engineering coordination application with no separable foundation. This is now proven, not theoretical: six application repos (devtown, aml, clinical, life, drafthouse, quarkmind) share the same foundation, each adding domain logic without modifying the foundation.

The second difference is **orchestration breadth**. Gastown drives agents through formula steps — workflow. CaseHub declares goals and discovers paths — Adaptive Case Management. For AI agent coordination where outputs determine next steps, ACM is the correct paradigm.

The third difference — new since v2 — is **AI-native operations**. CaseHub's 11 MCP tools make the harness operable by AI agents via protocol, not just humans via shell. An AI agent can monitor the queue, detect a stalled reviewer, check trust scores, and trigger a retry through the same protocol it receives work on. Gastown's `gt` CLI is shell-only; agents are workers, never operators.

The fourth difference — new since v3 — is **merge queue architecture**. The merge queue spec (devtown#11) is complete. CaseHub's batch-then-bisect is a CasePlanModel with trust-weighted bisection, priority lanes, dependency-aware ordering, SLA-bounded queue wait, and adaptive batch sizing. Ten architectural capabilities that go beyond what traditional Bors-style merge queues provide. Two foundation gates (engine#573, engine#574) are the only prerequisites before implementation.

What changed since v3: the merge queue design is complete with spec at v5 (5 review rounds, 15 terminal paths verified, schema-aligned to `CaseDefinition.yaml`). The competitive picture on application features has shifted — CaseHub's merge queue design demonstrates structural advantages in the core feature where Gastown has production maturity.

---

## 2. Current State — What's Built

### Foundation Layers (all shipped)

| Layer | What it adds | Key issue | What it proves |
|-------|-------------|-----------|---------------|
| 1 | Domain baseline — vocabulary model, `@DefaultBean` service, REST entry point | devtown#8, #9, #27 | Domain model separates cleanly from foundation |
| 2 | + casehub-work — SLA-bounded human review gate with two-tier escalation | devtown#41, #42 | WorkItem lifecycle serves human review; SLA breach policy works end-to-end |
| 3 | + casehub-qhorus — typed COMMAND/RESPONSE/DONE/DECLINE per reviewer | devtown#52 | Normative commitment lifecycle captures every agent interaction |
| 4 | + casehub-ledger — tamper-evident merge decision audit trail | devtown#73, #7 | MergeDecisionLedgerEntry + ComplianceSupplement + compliance report endpoint |
| 5 | + casehub-engine — CasePlanModel, content-driven routing, parallel checks | devtown#10 | ACM bindings fire reactively; security review triggers on code content, not labels |
| 6 | + trust routing — trust-weighted reviewer assignment from outcome attestations | devtown#57 | `TrustWeightedAgentStrategy` wired; per-capability routing policies operational |

### Additional Shipped Capabilities

| Capability | Issue | What it does |
|-----------|-------|-------------|
| GDPR Art.17 erasure endpoint | devtown#74 | Tamper-evident erasure receipt; `sha256()` utility; PII echo removed from responses |
| ActionRiskClassifier oversight gate | devtown#56 | 8 action types, 4 classification categories, PreferenceProvider-driven thresholds; human approval gates for consequential actions |
| Compliance report | devtown#7 | Per-case evidence across four EU AI Act Art.12 dimensions |
| Post-merge trust feedback | devtown#5 | FLAGGED attestation on incident-linked review — trust score degrades for missed findings |
| HITL end-to-end (happy path + escalation) | devtown#33, #30 | WorkItem COMPLETED signals case; SLA escalation signals case (engine#349, work#225 both shipped) |
| Observability + operational MCP tools | devtown#17 | 11 MCP tools (8 read + 3 write) + W3C PROV-DM export + event-sourced PrReviewCaseTracker; full Gastown CLI parity with AI-native additions |

### The Closed Feedback Loop

This is the architectural headline. All three links are now wired:

```
Prescriptive (casehub-engine)  → assigns work via COMMAND
                                  TrustWeightedAgentStrategy selects by capability trust score
Normative (casehub-qhorus)     → agent acknowledges (OPEN→ACKNOWLEDGED) and fulfills (→FULFILLED)
                                  DECLINED is a positive signal (agent knows its limits)
Evaluative (casehub-ledger)    → FULFILLED writes LedgerAttestation (SOUND)
                                  TrustScoreJob updates Bayesian Beta model
                                  TrustScoreCache hydrates; next assignment uses new score
```

No human intervention. No configuration change. No stamps assigned. The system improves its routing by operating. Gastown has no mechanism to close this loop.

---

## 3. Foundation vs Foundation

Infrastructure-to-infrastructure comparison, stripping application-layer concerns from both sides.

### 3.1 Coordination Model

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Paradigm | Workflow (formula steps, fixed at design time) | ACM (goals, emergent paths via blackboard) | **CaseHub** — ACM is the correct paradigm for agent coordination where outputs determine next steps |
| Driving force | Agent processes hook (GUPP) | Context change fires binding evaluation | **CaseHub** — engine-initiated, zero-latency between worker completion and next scheduling decision |
| Parallelism | Explicitly declared in formula | Automatic — all bindings whose conditions are simultaneously satisfied fire at once | **CaseHub** — adding a new check type is one binding, not restructuring the parallel declaration |
| Failure handling | Agent-side logic or Witness re-assignment | Engine re-evaluates bindings with updated context; alternative paths fire automatically | **CaseHub** — failure is new information on the blackboard, not an error to recover from |
| Synchronous wait | None | WAITING state with durable PendingWorkRegistry | **CaseHub** — human approval + CI run in parallel; no synchronous wait primitive in Gastown |
| Hybrid modes | No | Choreography + orchestration per case, no pre-commitment | **CaseHub** — fan out reactively, suspend for human decision, resume; single case, no mode switch |

### 3.2 Normative Layer

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Communication types | 2 informal (nudge, sling) | 9 speech-act types (Searle taxonomy) | **CaseHub** — sharpest gap; provably complete classification; no 10th type to discover |
| Obligation tracking | None | 7-state Commitment lifecycle | **CaseHub** — Gastown has no concept of who promised what to whom |
| Trust from outcomes | None | FULFILLED → LedgerAttestation → TrustScoreJob | **CaseHub** — the closed feedback loop is live; trust updates from every agent interaction |
| DECLINED vs FAILED | Indistinguishable | Structurally distinct commitments | **CaseHub** — "I can't do this" vs "I tried and failed" are different operational responses |
| Delegation chain | History lost on re-sling | HANDOFF with full `causedByEntryId` chain | **CaseHub** — six months later the chain is readable, not reconstructed |
| Stalled detection | Witness timeout | `list_stalled_obligations` + WatchdogEvaluationService | **Parity** on detection; Gastown has recovery, CaseHub does not (§3.6) |

### 3.3 Trust and Reputation

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Model | Human-curated stamps | Bayesian Beta (auto-computed from attestations) | **CaseHub** — mathematically grounded; no human curation required |
| Capability-scoped scoring | No | `ScoreType.CAPABILITY` with per-capability thresholds | **CaseHub** — an agent's security-review quality is tracked separately from style-review |
| Per-dimension quality | No | `ScoreType.CAPABILITY_DIMENSION` quality floors | **CaseHub** — review thoroughness and precision scored independently |
| Routing integration | Stamps don't drive routing | `TrustWeightedAgentStrategy @Priority(1)` — live | **CaseHub** — trust scores directly drive assignment; Gastown stamps are informational only |
| Cold-start handling | No prior = unknown | Four-phase maturity model; Phase 0 = Gastown parity | **CaseHub** — never blocks on missing trust data; improves automatically as evidence accumulates |
| Temporal decay | None | Exponential decay; FLAGGED persistence multiplier | **CaseHub** — recent evidence counts more; negative signals persist longer |
| Sybil resistance | None | EigenTrust (Kamvar 2003) | **CaseHub** — provably collusion-resistant transitive trust |
| Cross-deployment | Wasteland stamps via DoltHub (production) | TrustExportService + TrustImportService (shipped, ledger#63-65) | **Parity** — different basis: Gastown's stamps are human-curated; CaseHub's trust carries cryptographic provenance |

### 3.4 Audit and Compliance

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Tamper evidence | Dolt git-for-SQL (admin-trusted) | Merkle MMR (cryptographic, independently verifiable) | **CaseHub** — admin cannot rewrite Merkle history; inclusion proofs verifiable without server access |
| Ed25519 bilateral signing | No | Platform signs COMMAND, agent signs RESPONSE | **CaseHub** — neither side can repudiate; compromise detection via CDI event |
| Inclusion proofs | No | Publishable to external transparency log | **CaseHub** — third-party verification without server access |
| GDPR Art.17 erasure | No | LedgerErasureService + tamper-evident receipt | **CaseHub** — prove data was erased, prove when, prove chain integrity preserved |
| GDPR Art.22 decision records | No | ComplianceSupplement (EU AI Act Art.12 fields) | **CaseHub** — purpose-built regulatory compliance, not generic logging |
| W3C PROV-DM lineage | No | LedgerProvExportService + `causedByEntryId` chain | **CaseHub** — explicit causal chain, not reconstructed from logs |
| Cross-repo causal chain | Implicit in bead history | Explicit `causedByEntryId` (claudony#94 shipped) | **CaseHub** — lineage crosses repo boundaries without breaking |

### 3.5 Human-in-the-Loop

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Human task model | Bead (same as agent) | WorkItem — 10-status lifecycle | **CaseHub** — dedicated model for human work with distinct semantics from agent work |
| SLA enforcement | None | expiresAt + ClaimDeadlineJob + ExpiryCleanupJob | **CaseHub** — time-bounded human obligations with automatic escalation |
| Delegation | None | WorkItem DELEGATED + EscalationPolicy | **CaseHub** — formal reassignment with audit trail |
| Escalation policy | Three-tier severity | SlaBreachPolicy SPI (pluggable per scenario) | **CaseHub** — both support escalation; CaseHub's is pluggable and application-specific |
| Case integration | Bead completion = no case signal | WorkItemLifecycleEvent → case signal (happy path + escalation) | **CaseHub** — WorkItem completion and SLA breach both signal the case engine |
| Action oversight | None | ActionRiskClassifier SPI — gates consequential actions via WorkItem | **CaseHub** — no Gastown equivalent; agents execute autonomously in formula steps |

### 3.6 Agent Oversight and Recovery

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Detection | Witness timeout + Deacon patrol + Boot validation | WatchdogEvaluationService + `list_stalled_obligations` | **Parity** on detection — both identify stuck agents |
| Recovery | Re-assignment, agent restart, infrastructure validation | Detection alerts only — no automated recovery | **Gastown** — CaseHub's most significant gap; `RecoveryPolicy` SPI designed but not implemented |
| Recovery hierarchy | Three tiers (Witness → Deacon → Boot) | None | **Gastown** — hierarchical oversight is one of Gastown's strongest capabilities |
| Concurrency control | Scheduler (per-session API rate limit) | Not built | **Gastown** — hard failure at 10+ concurrent cases without throttle |

### 3.7 Extensibility

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Extension model | Plugin system (stateful, patrol-driven) | SPI-based (compile-time verified) | **CaseHub** — misconfigured SPI fails at build time, not under load |
| Ecosystem | Go stdlib ~27 deps (closed) | Full Quarkiverse (Kafka, Redis, gRPC, Elasticsearch, etc.) | **CaseHub** — open ecosystem vs closed dependency set |
| Multi-domain | Single application | 6 application repos on shared foundation | **CaseHub** — proven, not theoretical; Gastown can only ever be one application |

---

## 4. Application vs Application

### 4.1 What Gastown Provides

Production at v1.0.1. Battle-tested.

| Capability | Mechanism |
|-----------|-----------|
| Merge queue | Refinery — Bors batch-then-bisect |
| AI coding agents | Polecats with persistent bead identity |
| Human workspaces | Crew — long-lived, full git clone |
| Cross-rig routing | `routes.jsonl` transparent bead routing |
| Workflow templates | Formulas (TOML) with role directives and overlays |
| CLI | `gt feed`, `gt problems`, `gt doctor`, `gt seance`, `gt stale`, `gt peek` |
| Predecessor context | `gt seance` — agents query prior session decisions |
| Federated reputation | Wasteland stamps via DoltHub |
| Sandboxed execution | `gt-proxy-server` with mTLS |

### 4.2 What casehub-devtown Provides

Pre-production. Six layers shipped, actively building.

| Capability | Foundation primitive | Status |
|-----------|---------------------|--------|
| Content-driven PR routing | CasePlanModel — bindings gate on code content | ✅ Layer 5 |
| Human review gate with SLA | WorkItem + SlaBreachPolicy SPI | ✅ Layer 2 |
| Typed agent messaging | Qhorus COMMAND/RESPONSE/DONE/DECLINE per reviewer | ✅ Layer 3 |
| Tamper-evident merge audit | MergeDecisionLedgerEntry + ComplianceSupplement | ✅ Layer 4 |
| Trust-weighted reviewer routing | TrustWeightedAgentStrategy + DevtownTrustRoutingPolicyProvider | ✅ Layer 6 |
| GDPR Art.17 erasure | LedgerErasureService with tamper-evident receipt | ✅ devtown#74 |
| Action oversight gates | DevtownActionRiskClassifier — 8 types, 4 categories | ✅ devtown#56 |
| Compliance report | CodeReviewComplianceService — 4 regulatory dimensions | ✅ devtown#7 |
| Post-merge trust feedback | FLAGGED attestation on incident-linked review | ✅ devtown#5 |
| Failure goal support | CasePlanModel `kind: failure` | ✅ engine#326 |
| HITL end-to-end (happy + escalation) | casehub-work-adapter + CaseSignalSink | ✅ devtown#33 |
| Cross-deployment reputation | TrustExportService + TrustImportService | ✅ Inherited |
| Human + CI parallel execution | WAITING state — total time = max, not sum | ✅ Layer 5 |
| Observability + operational tooling | 11 MCP tools (8 read + 3 write) + W3C PROV-DM + event-sourced tracker | ✅ devtown#17 |
| Merge queue | CasePlanModel — batch-then-bisect with trust-weighted bisection | ⚙️ Spec complete (devtown#11) — gates: engine#573, engine#574 |
| GitHub integration | Webhook receiver, CI status, merge execution | Not started |

### 4.3 Gastown Feature Parity Checklist

| Gastown feature | devtown approach | Status |
|----------------|-----------------|--------|
| Merge queue (Bors batch-then-bisect) | CasePlanModel + recursive bisect sub-cases + trust-weighted split | ⚙️ Spec complete — 10 structural improvements over Bors (§5.9) |
| AI coding agent workers | Claudony WorkerProvisioner | Foundation ready |
| Human workspaces (Crew) | Human review WorkItem via casehub-work | ✅ Layer 2 |
| Cross-rig agent routing | Sub-case orchestration | Foundation ready |
| CLI tooling | 11 MCP tools (8 read + 3 write) — AI-native, protocol-native | ✅ devtown#17 |
| Predecessor session context | `get_prior_decisions` MCP tool + CaseMemoryStore | ✅ Partial (full requires Doltgres P1.5) |
| Federated reputation (Wasteland) | TrustExportService + TrustImportService | ✅ Shipped |
| Sandboxed execution | gt-proxy-server equivalent | Not planned |
| Agent concurrency control | SpawnThrottle in ClaudonyConfig | Not started |
| Hierarchical watchdog | RecoveryPolicy SPI | Not started |

---

## 5. Where CaseHub Surpasses Gastown

Capabilities that require a structural rewrite in Gastown to match — not bolt-on features.

### 5.1 The Closed Feedback Loop

The prescriptive→normative→evaluative→prescriptive cycle is live. Trust scores update from agent behaviour, driving future routing, without human intervention. Gastown cannot close this loop because it has no normative layer to generate the evaluative signal. Adding status fields to beads gives you tracking, not accountability — and accountability semantics are what trust scoring requires to be meaningful.

### 5.2 Action Oversight Gates

Workers return a `PlannedAction` in their `WorkerResult`. The engine gates consequential actions through a human WorkItem before the case advances. Eight action types, four classification categories (ROUTINE, ELEVATED, HIGH, CRITICAL), PreferenceProvider-driven thresholds. An agent that wants to force-push to main gets stopped and routed to a human. Gastown has no platform-level intercept between "agent decided to act" and "action executed."

### 5.3 GDPR-Compliant Tamper-Evident Erasure

The erasure endpoint does not just delete records — it writes a tamper-evident receipt to the Merkle chain. You can prove data was erased, prove when, and prove chain integrity was preserved. The compliance report assembles per-case evidence across four EU AI Act Art.12 dimensions. For regulated environments, this is table stakes. Gastown has no compliance story without rebuilding its persistence layer.

### 5.4 Two-Tier Human Involvement

`HumanDecision` (formal PR accountability — WorkItem with SLA, escalation, delegation, trust accumulation) and `HumanOversight` (routing confidence review — triggered by borderline trust scores, fleet gaps, or insufficient observations). Gastown treats humans identically to agents: bead on hook. The SLA breach policy, ActionRiskClassifier, and trust maturity model's automatic escalation compose into a governance surface Gastown has no path to without a WorkItem-equivalent abstraction.

### 5.5 Trust Model Depth

Capability-scoped Bayesian Beta scoring. Per-capability quality floors (dimension scoring). Four-phase maturity model with automatic transitions. Configurable per-capability routing policies (threshold, minimumObservations, borderlineMargin, blendFactor). Trust-weighted routing driving actual assignment. Cross-deployment federation with mathematical provenance. Gastown has human-curated stamps that do not feed back into routing.

### 5.6 Content-Driven Adaptive Routing

Security review fires because code analysis found cryptographic code — not because a human labelled the PR. Multiple specialist checks fire simultaneously from binding conditions without explicit parallel declarations. Adding a new check type is one binding definition. In Gastown, it is restructuring the formula.

### 5.7 Proven Multi-Domain Layering

Six application repos (devtown, aml, clinical, life, drafthouse, quarkmind) share the same foundation. Each adds domain logic without modifying the foundation. The strategic moat is demonstrated, not theoretical. Gastown can only ever be one application.

### 5.8 AI-Native Operational Tooling

11 MCP tools provide the operational surface via protocol — not shell commands. An AI agent can monitor the review queue (`get_queue_status`), detect a stalled reviewer (`list_problems`), check trust scores (`get_reviewer_health`), trigger a retry (`retry_reviewer`), and export the provenance chain (`export_prov`) — all through the same MCP protocol it uses for its own work. Gastown's `gt` CLI is shell-only: agents are workers in Gastown, never operators. CaseHub's MCP surface means agents can self-monitor the system they participate in, closing the loop between coordination and observability. The three write tools (`retry_reviewer`, `reroute_review`, `force_complete_check`) provide operator intervention capabilities Gastown does not have in any form — Gastown's recovery is fully automated (Witness/Deacon) with no human or agent intervention surface.

### 5.9 Merge Queue — Ten Architectural Differences

The merge queue (devtown#11) is the first CaseHub feature that directly addresses Gastown's core production capability. CaseHub's design takes a structurally different approach — a CasePlanModel where the merge strategy is binding conditions, not compiled code. The architecture is two-tier: an imperative `MergeQueueService` for queue management (priority, dependency DAG, batch formation) and a reactive `merge-batch` CasePlanModel for batch processing (test, bisect, merge, reject).

Each capability below reflects a structural architectural difference between the traditional Bors-style approach and CaseHub's CasePlanModel-based design:

| # | Capability | Traditional (Bors-style) | CaseHub |
|---|-----------|-------------------------|---------|
| 1 | Strategy as data | Compiled code | CasePlanModel YAML, per repo at runtime |
| 2 | Trust-weighted batch composition | FIFO batching | Batch size ∝ min trust; low-trust PRs get smaller batches |
| 3 | Trust-weighted bisection | Positional binary split | Split by trust — isolate likely culprits first |
| 4 | Priority lanes with starvation prevention | FIFO ordering | Composite score with time-decay (125 pts/hr default) |
| 5 | Dependency-aware ordering | Author-managed | DAG from labels + git base-branch |
| 6 | SLA-bounded queue wait | Not tracked | WorkItem per queued PR, tiered escalation |
| 7 | Adaptive batch sizing | Static configuration | Batch size adapts to recent failure rate (floor of 1) |
| 8 | Cryptographic merge audit | Application logs | MergeDecisionLedgerEntry in Merkle chain |
| 9 | Human oversight for high-risk merges | Uniform merge path | ActionRiskClassifier gates by risk level |
| 10 | Recursive auditable bisection | Internal to the algorithm | Every level in EventLog with causal chain |

**Trust-weighted bisection** is the headline. Traditional merge queues bisect mechanically — split by position, no information about which half contains the culprit. CaseHub sorts PRs by trust score before splitting, clustering low-trust PRs (statistically more likely to fail) in one sub-batch. A pluggable `BisectionSplitStrategy` SPI supports three implementations: `TrustWeightedSplitStrategy` (default), `IsolateOutlierStrategy` (>2σ outlier isolation), `BinarySplitStrategy` (positional, for benchmarking).

**Recursive sub-cases** enable the CasePlanModel to bisect by spawning two sub-batch cases using the same model definition. Each level of bisection is a full case with its own EventLog entries, trust-weighted agent selection, and human escalation path. The bisection tree is auditable end-to-end via `get_causal_chain`. Foundation gate: engine#573 (bounded recursive sub-case depth limit).

**M-of-N grouped sub-cases** enable parallel bisection — both halves test simultaneously, parent waits for both via `groupId` with `requiredCount: 2`. Each child writes to a separate output key (`bisectLeft`/`bisectRight`). Foundation gate: engine#574 (M-of-N fields in YAML schema + per-child outputMapping fix).

Full spec: `specs/2026-06-26-merge-queue-design.md` (workspace).

---

## 6. Where Gastown Still Leads

Not minimised. These are real operational advantages.

### 6.1 Operational Maturity

Gastown is v1.0.1, in production, with a known failure profile and operational tooling built from experience. CaseHub is pre-production. This is the most significant single fact.

### 6.2 Hierarchical Recovery

Witness monitors per-rig polecats. Deacon monitors cross-rig. Boot validates Deacon every 5 minutes. Each tier detects failure and takes action — re-assignment, restart, infrastructure validation. CaseHub has detection (WatchdogEvaluationService, `list_stalled_obligations`) but no automated recovery action. The `RecoveryPolicy` SPI is designed but not implemented. At 20+ agents, manual recovery is operationally unsustainable. This is CaseHub's most significant gap.

### 6.3 Concurrency Control

Gastown's Scheduler prevents Claude API rate limit exhaustion at the session level. CaseHub's WorkerProvisioner spawns sessions without throttle. At 10+ concurrent cases this becomes a hard failure, not a degradation.

### 6.4 Operational Tooling — Narrowed

Gastown's `gt` CLI provides 6 commands: `gt feed`, `gt problems`, `gt doctor`, `gt seance`, `gt stale`, `gt peek`. CaseHub now provides 11 MCP tools (devtown#17) that cover the same operational surface plus capabilities Gastown lacks:

| Gastown command | CaseHub MCP tool | Parity |
|----------------|-----------------|--------|
| `gt feed` | `get_recent_events` (ring buffer, since filter) | ✅ Parity |
| `gt problems` | `list_problems` (stalled, expired, failed — configurable threshold) | ✅ Parity |
| `gt doctor` | `get_system_health` (fleet size, avg trust, open commitments) | ✅ Parity |
| `gt seance` | `get_prior_decisions` (CaseMemoryStore recall by repo + file path) | ⚠️ Partial — full requires Doltgres time-travel (P1.5) |
| `gt stale` | `list_problems` (stalled case detection included) | ✅ Parity |
| `gt peek` | `inspect_review` (full EventLog timeline + per-capability status) | ✅ Parity |
| — | `get_reviewer_health` (trust scores, dimensions, decision count) | **CaseHub only** |
| — | `export_prov` (W3C PROV-DM per case) | **CaseHub only** |
| — | `retry_reviewer`, `reroute_review`, `force_complete_check` | **CaseHub only** — operator intervention tools |

The remaining gap is **AI-native vs human-only**: CaseHub's MCP tools are protocol-native — AI agents can operate the harness directly. Gastown's CLI is shell-only. An agent monitoring a review queue, detecting a stalled reviewer, and triggering a retry is structurally possible in CaseHub and structurally impossible in Gastown.

### 6.5 The Pattern

Every area where Gastown leads is **operational** — things needed to run at scale. Every area where CaseHub leads is **structural** — things that cannot be bolted on after the fact. Gastown's advantages can be built on CaseHub's foundation. CaseHub's advantages require Gastown to rebuild its foundation.

With Epic 10 shipped, the operational tooling gap has narrowed significantly. The remaining Gastown advantages are: production maturity (§6.1), hierarchical recovery automation (§6.2), and concurrency control (§6.3). Tooling is now at functional parity with CaseHub-only additions.

---

## 7. Remaining Roadmap

### 7.1 Foundation Gaps

| Gap | What it is | Priority | Impact |
|-----|-----------|----------|--------|
| engine#573 Recursive sub-cases | Replace circular guard with bounded `maxRecursionDepth` on SubCase | High | Blocks merge queue bisection (devtown#11) |
| engine#574 M-of-N YAML + outputMapping | Expose group fields in YAML schema; fix per-child outputMapping | High | Blocks parallel bisection (devtown#11) |
| P1.1 Concurrency throttle | `SpawnThrottle` in ClaudonyConfig — cap concurrent worker sessions | High | Hard failure at 10+ concurrent cases |
| P1.2 Recovery automation | `RecoveryPolicy` SPI — REPROVISION, ESCALATE_TO_HUMAN, CANCEL_CASE | High | Manual recovery unsustainable at 20+ agents |
| P1.5 Doltgres backend | Configurable `casehub.ledger.backend=doltgres` — time-travel, branching | Low | Nice-to-have for debugging; not blocking |

### 7.2 Application Epics

| Epic | What it is | Scale | Foundation gate |
|------|-----------|-------|----------------|
| #11 Merge queue | CasePlanModel batch-then-bisect with trust-weighted bisection | XL | ⚙️ Spec complete — engine#573 + engine#574 |
| #12 Cross-repo coordinated merge | Parent case + per-repo sub-cases | XL | #11 |
| #13 Trust-weighted reviewer routing (full) | PostMergeIncidentHandler + full trust feedback wiring | XL | Layer 6 ✅ |
| #14 Failure handling | DECLINED vs FAILED declarative routing | XL | P0 ✅ |
| #15 GitHub integration | Webhooks, CI status, merge execution | XL | None |
| #16 Notification wiring | casehub-connectors integration | XL | parent#5 ✅ |
| ~~#17 Operational tooling~~ | ~~MCP tools: queue status, reviewer health, merge audit~~ | ~~XL~~ | ✅ Shipped |

### 7.3 Where the Roadmap Leaves Us

**#17 (operational tooling) is shipped.** 11 MCP tools + W3C PROV-DM + event-sourced read model. The `gt` CLI gap is closed at functional parity with CaseHub-only additions (see §6.4).

**After P1.1 + P1.2:** CaseHub can run at agent scale. Gastown's two remaining operational advantages close.

**After #11 (merge queue):** CaseHub matches Gastown's core application feature. The merge strategy is binding conditions in a CasePlanModel — changeable without deployment. Different repos can use different strategies.

**After #15 (GitHub integration):** CaseHub connects to real PR events. Cases are triggered by webhooks, not REST calls. CI status flows in. Merges execute via GitHub API.

At that point, CaseHub has Gastown parity on application features plus structural advantages Gastown cannot match. The remaining gap is operational maturity — which only comes from running in production.

---

## 8. Critical Path to Demo

Three demo paths — architectural depth for engineers, governance value for managers, AI-native operations for both. The infrastructure is shipped. What's missing is a thin demo harness: mock workers and a scenario script that drives the existing capabilities end-to-end.

### What exists today

All of the following are shipped, tested, and wired:

**Layers 1–6 (core coordination):**
- CasePlanModel with JQ binding conditions (Layer 5)
- Content-driven routing — bindings fire on code analysis results (Layer 5)
- TrustWeightedAgentStrategy driving agent selection (Layer 6)
- Qhorus COMMAND/DONE commitment lifecycle (Layer 3)
- LedgerAttestation from commitment outcomes → TrustScoreJob (P0.2)
- WorkItem human review gate with SLA + two-tier escalation (Layer 2)
- MergeDecisionLedgerEntry + ComplianceSupplement (Layer 4)
- HITL end-to-end — happy path and escalation (devtown#33)
- PR review REST resource for case creation

**Cross-cutting capabilities:**
- ActionRiskClassifier oversight gate — 8 action types, 4 categories (devtown#56)
- Compliance report endpoint — 4 regulatory dimensions (devtown#7)
- GDPR erasure endpoint with tamper-evident receipt (devtown#74)
- Post-merge trust feedback — FLAGGED attestation on incident-linked review (devtown#5)

**Observability and operations (Epic 10, devtown#17):**
- 11 MCP tools — 8 read (`get_queue_status`, `get_recent_events`, `get_system_health`, `list_problems`, `inspect_review`, `get_reviewer_health`, `get_prior_decisions`, `export_prov`) + 3 write (`retry_reviewer`, `reroute_review`, `force_complete_check`)
- `PrReviewCaseTracker` — event-sourced read model with ring buffer
- W3C PROV-DM export via `LedgerProvExportService`

### What needs to be built

**Step 1: Mock lambda workers** — S · Low

Three CDI `@ApplicationScoped` lambda workers that write to the case blackboard:

- `MockCodeAnalysisWorker` — reads PR metadata, writes `{analysisComplete: true, securitySensitive: true/false}` based on file paths
- `MockSecurityReviewWorker` — writes `{securityCheck: {passed: true, findings: [...]}}` after a short delay
- `MockCiRunnerWorker` — writes `{ci: {status: "passing"}}` after a delay

These simulate the worker output shape so binding conditions fire correctly. Each is ~20 lines.

**Step 2: Trust score seeding** — XS · Low

A `DemoDataSeeder @Startup` bean (activated via a `demo` Quarkus profile) that:

- Registers 3 mock agents with `ActorTrustScore` rows at different capability-scoped scores
- Sets agent A above the security-review threshold (0.82), agent B below but close (0.61), agent C with no observations (Phase 0 bootstrap)
- Gives agent B enough history to cross the threshold after one more positive outcome
- Seeds CaseMemoryStore with prior review decisions for `src/auth/` path (makes `get_prior_decisions` demo self-contained)

This makes the trust routing and memory recall demos self-contained — no manual DB setup.

**Step 3: Demo scenario script** — S · Low

An `.http` file (IntelliJ HTTP Client format) with annotated sections for each demo path. Each section includes the REST call followed by the MCP tool query that reveals what happened underneath — the demo is driven by REST and observed via MCP.

### Delivery order and dependencies

```
Step 1 (mock workers) ─── no dependencies ──────────────────────┐
Step 2 (trust seeding) ── no dependencies ──────────────────────┤
                                                                 ├─→ Step 3 (demo script)
                                                                 │
PrReviewResource ── already exists ────────────────────────────┤
DevtownMcpTools ── already exists (11 tools) ──────────────────┘
```

Steps 1 and 2 are independent — can be built in parallel. Step 3 depends on both.

**Total estimate:** S-scale, Low complexity. The architectural work is done. This is glue code and a scenario script.

### Demo paths

**Path A — The Closed Feedback Loop** (platform engineers)

The architectural headline. Shows trust-weighted routing improving from operation alone.

| Step | Action | Observe via |
|------|--------|------------|
| 1 | Submit PR for `src/auth/TokenValidator.java` via REST | `get_queue_status` — case appears, status RUNNING |
| 2 | MockCodeAnalysisWorker fires, writes `securitySensitive: true` | `get_recent_events` — CONTEXT_CHANGED event in ring buffer |
| 3 | Security-review binding fires on content | `inspect_review` — security-review capability shows SCHEDULED |
| 4 | TrustWeightedAgentStrategy selects agent A (score 0.82 > 0.70 threshold) | `get_reviewer_health` for agent A — shows open commitment |
| 5 | COMMAND dispatched, agent A completes with DONE | `inspect_review` — security-review shows COMPLETED |
| 6 | LedgerAttestation (SOUND) → TrustScoreJob runs | `get_reviewer_health` for agent B — score now crosses threshold |
| 7 | Submit second PR for `src/auth/SessionManager.java` | `get_queue_status` — second case appears |
| 8 | Trust-weighted routing now selects agent B | `get_reviewer_health` for agent B — open commitment appears |
| 9 | Export provenance chain | `export_prov` — W3C PROV-DM shows full causal chain |

**What this proves:** No configuration change. No human intervention. The routing improved by operating. Agent B earned its way from Phase 0 to Phase 1 through demonstrated competence.

**Path B — Compliance and Governance** (engineering managers)

Shows every decision is tracked, every obligation enforced, every erasure provable.

| Step | Action | Observe via |
|------|--------|------------|
| 1 | Submit PR via REST | `get_queue_status` — case appears |
| 2 | WorkItem created for human gate | Direct REST query — WorkItem with SLA timer |
| 3 | Fast-forward SLA timer (test API) | `list_problems` — expired commitment appears |
| 4 | Escalation fires — WorkItem reassigned to `pr-leads` | `get_recent_events` — escalation event in buffer |
| 5 | Complete human review | `inspect_review` — human-decision shows COMPLETED |
| 6 | Agent proposes force-push (HIGH action type) | ActionRiskClassifier gate fires — WorkItem for approval |
| 7 | Approve action | `inspect_review` — case advances past gate |
| 8 | Query compliance report | REST `/compliance/{caseId}` — four regulatory dimensions |
| 9 | Call GDPR erasure | REST `/erasure` — tamper-evident receipt returned |

**What this proves:** Every assignment carries an SLA. Every SLA breach triggers escalation. Every consequential action is gated. Every decision has a Merkle audit trail. Every erasure is provable. Gastown has none of this.

**Path C — AI-as-Operator** (both audiences)

The capability Gastown structurally cannot have. An AI agent operates the coordination system via MCP.

| Step | Action | What it shows |
|------|--------|--------------|
| 1 | Agent calls `get_queue_status` | AI monitoring the review queue — not a human running shell commands |
| 2 | Agent calls `list_problems` — finds stalled reviewer | AI detecting operational issues in real time |
| 3 | Agent calls `get_reviewer_health` — reviews trust scores | AI reasoning about agent capability before intervention |
| 4 | Agent calls `retry_reviewer` — triggers retry | AI taking corrective action through the same protocol it receives work on |
| 5 | Agent calls `get_prior_decisions` for the stalled file path | AI using institutional memory to understand why this area is problematic |
| 6 | Agent calls `export_prov` — exports causal chain | AI producing audit artifacts for human review |

**What this proves:** The harness is not just coordinating AI agents — it can be operated by AI agents. Workers and operators share the same protocol. Gastown's `gt` CLI is shell-only; an AI agent cannot operate Gastown's tooling.

### Demo extras — additional elements that elevate the demo

These are already built and require no new code. Each can be added to any path.

| Element | How to show it | Why it's compelling |
|---------|---------------|-------------------|
| **Trust score before/after** | Call `get_reviewer_health` before and after a review completes | Audience sees the Bayesian Beta model update in real time — not a diagram, a live number change |
| **Phase 0 → Phase 1 transition** | Agent C starts with no observations, completes one review, `get_reviewer_health` shows transition | Cold-start handling is live, not theoretical |
| **Memory recall across cases** | Submit two PRs touching `src/auth/`, call `get_prior_decisions` after second | System remembers what it learned — contextual routing from institutional memory |
| **W3C PROV-DM as audit artifact** | `export_prov` produces PROV-JSON-LD, render in any PROV viewer | Standards-compliant provenance — interoperable with external audit tooling |
| **Operator override with audit** | `force_complete_check` with reason string, then query compliance report | Override is a first-class audited event, not a hack — the reason is in the Merkle chain |
| **Reroute as a recovery primitive** | Stall a reviewer, `reroute_review` — cancel and restart with same payload | One-step recovery that preserves the PR context — new case, new routing, same payload |
| **Fleet health dashboard** | `get_system_health` — fleet size, avg trust by capability, open commitments | System-level view: is the fleet healthy? Are trust scores trending up or down? |

### What's not in the demo

| Capability | Why not | When |
|-----------|---------|------|
| Merge queue | Epic #11 spec complete — implementation pending engine#573 + engine#574 | Post-demo (spec available for architectural walkthrough) |
| GitHub webhooks | Epic #15 — demo uses REST; real webhooks are an integration concern | Post-demo |
| Real AI agent review | Claudony + Claude CLI required — mock workers demonstrate the same routing and commitment semantics | Can layer in later |
| Doltgres time-travel | P1.5 — `get_prior_decisions` works via CaseMemoryStore without Doltgres | Not planned for demo |

---

## 9. Technology Stack

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Language | Go 1.25+ | Java 21 (on Java 26 JVM) |
| Persistence | Dolt SQL Server | PostgreSQL (default, Flyway) / Doltgres (configurable, roadmap) |
| Runtime | Go binary | GraalVM native image (0.084s) or JVM |
| Reactive model | Goroutines + patrol polling | Vert.x event loop + Mutiny |
| Workflow | Formula (TOML) + Molecules | Quarkus Flow (CNCF Serverless Workflow) |
| Binding conditions | None (agent cognition) | JQ + Java lambdas (+ Drools via SPI) |
| Message protocol | Proprietary (nudge/sling) | Qhorus (A2A compatible, MCP tools) |
| Observability | OTel comprehensive (per-agent run.id) | OTel + Merkle tamper evidence + W3C PROV-DM |
| Compliance | None | GDPR Art.17/22, EU AI Act Art.12, Merkle proofs |
| Agent support | Claude, Copilot, Gemini, Cursor, Codex | Claude (claudony), any via WorkerProvisioner SPI |
| Interface | `gt` CLI (comprehensive) | MCP tools + REST APIs |
| Extension model | Plugin system (5 gate types) | SPI-based (compile-time verified) |
| Ecosystem | Go stdlib ~27 deps (closed) | Full Quarkiverse |
| Version | v1.0.1 (production) | Pre-production (6 layers shipped) |
