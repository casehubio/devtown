# CaseHub vs Gastown: Architectural Analysis v5

> **Date:** 2026-07-07
> **Previous versions:** [v4](gastown-casehub-analysis-v4.md) (2026-06-26), [v3](gastown-casehub-analysis-v3.md) (2026-06-18), [v2](gastown-casehub-analysis-v2.md) (2026-05-25), [v1](gastown-casehub-analysis.md) (2026-04-27, archived — uses obsolete naming)

---

## 1. Executive Summary

Seven foundation layers, a merge queue, a governance UI, and 16 MCP tools have shipped in `casehub-devtown`. The closed feedback loop — where trust scores derived from cryptographically attested commitment outcomes drive future routing without human intervention — is live and strengthened by TrustGatedAttestationPolicy, which modulates attestation confidence based on the attesting agent's own trust score. CaseHub surpasses Gastown on model quality, compliance, trust, governance, merge queue architecture, and AI-native operations. Gastown leads on operational maturity, recovery automation, and concurrency control.

The central architectural difference remains **layering**. CaseHub is a domain-agnostic coordination foundation with domain applications on top. Gastown is a software engineering coordination application with no separable foundation. This is now proven at scale: eight reusable blocks (casehub-blocks) extracted from cross-domain patterns are shared across 6+ application repos. Trust routing, oversight gates, debate infrastructure, and accountability listeners are all shared without domain-specific modification.

The second difference is **orchestration breadth**. Gastown drives agents through formula steps — workflow. CaseHub declares goals and discovers paths — Adaptive Case Management. For AI agent coordination where outputs determine next steps, ACM is the correct paradigm.

The third difference is **AI-native operations**. CaseHub's 16 MCP tools make the harness operable by AI agents via protocol, not just humans via shell. Five merge queue tools shipped with Epic #11. Gastown's `gt` CLI is shell-only; agents are workers, never operators.

The fourth difference is **merge queue depth**. CaseHub's merge queue (Epic #11) is feature-complete: batch-then-bisect CasePlanModel with three pluggable bisection strategies, trust-weighted batch composition, adaptive batch sizing from failure rate, SLA per queued PR, and GitHub webhook admission. Both foundation gates (engine#573 recursive sub-cases, engine#574 M-of-N YAML schema) are closed. Ten architectural capabilities go beyond what traditional Bors-style merge queues provide.

The fifth difference is **trust pipeline depth**. TrustGatedAttestationPolicy (engine#668, devtown#97) makes the trust signal self-reinforcing: an agent with a high trust score produces higher-confidence attestations, which compound faster in Bayesian scoring. An agent with borderline trust produces attenuated signals, requiring more observations to influence routing. Gastown has no trust pipeline at all.

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

### Merge Queue (Epic #11 — shipped)

| Component | Issue | What it does |
|-----------|-------|-------------|
| Batch-then-bisect CasePlanModel | devtown#11 | Merge strategy as YAML — changeable per repo without deployment |
| Three bisection strategies | devtown#11 | TrustWeightedSplitStrategy (default), IsolateOutlierStrategy (>2sigma outlier), BinarySplitStrategy (positional baseline) |
| Trust-weighted batch composition | devtown#11 | Batch size proportional to minimum trust score in batch; low-trust PRs get smaller batches |
| Adaptive batch sizing | devtown#103 | Batch size adapts to recent failure rate (floor of 1) |
| SLA per queued PR | devtown#11 | WorkItem per queued PR with tiered escalation |
| GitHub webhook admission | devtown#101 | Labeled events enqueue PRs into the merge queue |
| Recursive sub-cases (foundation) | engine#573 | Bounded recursive sub-case depth for bisection tree |
| M-of-N grouped sub-cases (foundation) | engine#574 | Parallel bisection — both halves test simultaneously |

### Governance and UI

| Capability | Issue | What it does |
|-----------|-------|-------------|
| PR governance workbench | devtown#85 | Dashboard for case lifecycle, supersede/relink operations |
| casehub-pages Quinoa integration | devtown#92 | UI composition without full rebuild; hot-reload in dev mode |

### Trust Pipeline

| Capability | Issue | What it does |
|-----------|-------|-------------|
| TrustGatedAttestationPolicy | engine#668, devtown#97 | DONE attestation confidence scales with attesting agent's trust: BOOTSTRAP=0.7, QUALIFIED=boosted, BORDERLINE=0.7, BELOW_THRESHOLD=scaled down (floor 0.05). Displaces StoredCommitmentAttestationPolicy via `@Alternative @Priority(1)` |

### Additional Shipped Capabilities

| Capability | Issue | What it does |
|-----------|-------|-------------|
| GDPR Art.17 erasure endpoint | devtown#74 | Tamper-evident erasure receipt; `sha256()` utility; PII echo removed from responses |
| ActionRiskClassifier oversight gate | devtown#56 | 8 action types, 4 classification categories, PreferenceProvider-driven thresholds; human approval gates for consequential actions |
| Compliance report | devtown#7 | Per-case evidence across four EU AI Act Art.12 dimensions |
| Post-merge trust feedback | devtown#5 | FLAGGED attestation on incident-linked review — trust score degrades for missed findings |
| HITL end-to-end (happy path + escalation) | devtown#33, #30 | WorkItem COMPLETED signals case; SLA escalation signals case |
| Observability + operational MCP tools | devtown#17 | 16 MCP tools (11 read + 5 write) + W3C PROV-DM export + event-sourced PrReviewCaseTracker |

### casehub-blocks — Foundation Consolidation

Eight reusable blocks extracted from cross-domain patterns, shared across 6+ application repos:

| Block | Issue | What it provides |
|-------|-------|-----------------|
| Trust routing config | blocks#17 | TrustRoutingPolicyKeys, TrustRoutingPolicyResolver — shared trust configuration |
| Conversation protocol | blocks#22 | ConversationProtocol, ConversationProjection, ConversationRenderer — debate channels |
| Oversight gates | blocks#23 | OversightGateService, ActionRiskClassifier — shared oversight infrastructure |
| AI routing | blocks#30 | LlmAgentRoutingStrategy, CbrAgentRoutingStrategy — pluggable AI routing |
| Accountability listeners | blocks#12 | EventLogListener, LedgerExecutionListener — routing accountability |
| OTel metrics | blocks#21 | MetricsListener — OpenTelemetry instrumentation |
| RAG-enriched routing | blocks#16 | RAG-enriched supervisor routing strategy |
| Inter-agent channels | blocks#18 | Inter-agent channel setup for multi-agent coordination |

This is the proof of multi-domain consolidation. Trust routing, oversight gates, debate infrastructure, and accountability listeners are not devtown-specific — they are shared across domains without modification.

### The Closed Feedback Loop

All three links are wired, and the trust signal is now self-reinforcing:

```
Prescriptive (casehub-engine)  -> assigns work via COMMAND
                                  TrustWeightedAgentStrategy selects by capability trust score
Normative (casehub-qhorus)     -> agent acknowledges (OPEN->ACKNOWLEDGED) and fulfills (->FULFILLED)
                                  DECLINED is a positive signal (agent knows its limits)
Evaluative (casehub-ledger)    -> FULFILLED writes LedgerAttestation
                                  TrustGatedAttestationPolicy modulates confidence by trust tier
                                  TrustScoreJob updates Bayesian Beta model
                                  TrustScoreCache hydrates; next assignment uses new score
```

No human intervention. No configuration change. No stamps assigned. The system improves its routing by operating. TrustGatedAttestationPolicy (devtown#97) adds a compounding effect: high-trust agents produce stronger attestations, accelerating trust convergence for agents they interact with. Gastown has no mechanism to close this loop.

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
| Trust from outcomes | None | FULFILLED -> LedgerAttestation -> TrustScoreJob | **CaseHub** — the closed feedback loop is live; trust updates from every agent interaction |
| DECLINED vs FAILED | Indistinguishable | Structurally distinct commitments | **CaseHub** — "I can't do this" vs "I tried and failed" are different operational responses |
| Delegation chain | History lost on re-sling | HANDOFF with full `causedByEntryId` chain | **CaseHub** — six months later the chain is readable, not reconstructed |
| Stalled detection | Witness timeout | `list_stalled_obligations` + WatchdogEvaluationService | **Parity** on detection; Gastown has recovery, CaseHub does not (S3.6) |

### 3.3 Trust and Reputation

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Model | Human-curated stamps | Bayesian Beta (auto-computed from attestations) | **CaseHub** — mathematically grounded; no human curation required |
| Capability-scoped scoring | No | `ScoreType.CAPABILITY` with per-capability thresholds | **CaseHub** — an agent's security-review quality is tracked separately from style-review |
| Per-dimension quality | No | `ScoreType.CAPABILITY_DIMENSION` quality floors | **CaseHub** — review thoroughness and precision scored independently |
| Routing integration | Stamps don't drive routing | `TrustWeightedAgentStrategy @Priority(1)` — live | **CaseHub** — trust scores directly drive assignment; Gastown stamps are informational only |
| Confidence modulation | No | TrustGatedAttestationPolicy — attestation confidence scales with trust tier | **CaseHub** — high-trust agents produce stronger signals; borderline agents produce attenuated signals (devtown#97) |
| Cold-start handling | No prior = unknown | Four-phase maturity model; Phase 0 = Gastown parity | **CaseHub** — never blocks on missing trust data; improves automatically as evidence accumulates |
| Temporal decay | None | Exponential decay; FLAGGED persistence multiplier | **CaseHub** — recent evidence counts more; negative signals persist longer |
| Sybil resistance | None | EigenTrust (Kamvar 2003) | **CaseHub** — provably collusion-resistant transitive trust |
| Cross-deployment | Wasteland stamps via DoltHub (production) | TrustExportService + TrustImportService (shipped, ledger#63-65) | **Parity** — different basis: Gastown's stamps are human-curated; CaseHub's trust carries cryptographic provenance |

### 3.4 Audit and Compliance

Unchanged from v4.

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

Unchanged from v4.

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Human task model | Bead (same as agent) | WorkItem — 10-status lifecycle | **CaseHub** — dedicated model for human work with distinct semantics from agent work |
| SLA enforcement | None | expiresAt + ClaimDeadlineJob + ExpiryCleanupJob | **CaseHub** — time-bounded human obligations with automatic escalation |
| Delegation | None | WorkItem DELEGATED + EscalationPolicy | **CaseHub** — formal reassignment with audit trail |
| Escalation policy | Three-tier severity | SlaBreachPolicy SPI (pluggable per scenario) | **CaseHub** — both support escalation; CaseHub's is pluggable and application-specific |
| Case integration | Bead completion = no case signal | WorkItemLifecycleEvent -> case signal (happy path + escalation) | **CaseHub** — WorkItem completion and SLA breach both signal the case engine |
| Action oversight | None | ActionRiskClassifier SPI — gates consequential actions via WorkItem | **CaseHub** — no Gastown equivalent; agents execute autonomously in formula steps |

### 3.6 Agent Oversight and Recovery

Unchanged from v4.

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Detection | Witness timeout + Deacon patrol + Boot validation | WatchdogEvaluationService + `list_stalled_obligations` | **Parity** on detection — both identify stuck agents |
| Recovery | Re-assignment, agent restart, infrastructure validation | Detection alerts only — no automated recovery | **Gastown** — CaseHub's most significant gap; `RecoveryPolicy` SPI designed but not implemented |
| Recovery hierarchy | Three tiers (Witness -> Deacon -> Boot) | None | **Gastown** — hierarchical oversight is one of Gastown's strongest capabilities |
| Concurrency control | Scheduler (per-session API rate limit) | Not built | **Gastown** — hard failure at 10+ concurrent cases without throttle |

### 3.7 Extensibility

| Dimension | Gastown | CaseHub | Notes |
|-----------|---------|---------|-------|
| Extension model | Plugin system (stateful, patrol-driven) | SPI-based (compile-time verified) | **CaseHub** — misconfigured SPI fails at build time, not under load |
| Ecosystem | Go stdlib ~27 deps (closed) | Full Quarkiverse (Kafka, Redis, gRPC, Elasticsearch, etc.) | **CaseHub** — open ecosystem vs closed dependency set |
| Multi-domain | Single application | 6 application repos on shared foundation | **CaseHub** — proven, not theoretical; Gastown can only ever be one application |
| Cross-domain reuse | Not applicable (single app) | 8 reusable blocks (casehub-blocks) shared across 6+ repos | **CaseHub** — trust routing, oversight, debate, accountability extracted into shared blocks (blocks#12, #16, #17, #18, #21, #22, #23, #30) |

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

Pre-production. Six layers + merge queue + governance shipped, actively building.

| Capability | Foundation primitive | Status |
|-----------|---------------------|--------|
| Content-driven PR routing | CasePlanModel — bindings gate on code content | ✅ Layer 5 |
| Human review gate with SLA | WorkItem + SlaBreachPolicy SPI | ✅ Layer 2 |
| Typed agent messaging | Qhorus COMMAND/RESPONSE/DONE/DECLINE per reviewer | ✅ Layer 3 |
| Tamper-evident merge audit | MergeDecisionLedgerEntry + ComplianceSupplement | ✅ Layer 4 |
| Trust-weighted reviewer routing | TrustWeightedAgentStrategy + DevtownTrustRoutingPolicyProvider | ✅ Layer 6 |
| Trust-gated attestation | TrustGatedAttestationPolicy — confidence modulation by trust tier | ✅ devtown#97 |
| GDPR Art.17 erasure | LedgerErasureService with tamper-evident receipt | ✅ devtown#74 |
| Action oversight gates | DevtownActionRiskClassifier — 8 types, 4 categories | ✅ devtown#56 |
| Compliance report | CodeReviewComplianceService — 4 regulatory dimensions | ✅ devtown#7 |
| Post-merge trust feedback | FLAGGED attestation on incident-linked review | ✅ devtown#5 |
| Failure goal support | CasePlanModel `kind: failure` | ✅ engine#326 |
| HITL end-to-end (happy + escalation) | casehub-work-adapter + CaseSignalSink | ✅ devtown#33 |
| Cross-deployment reputation | TrustExportService + TrustImportService | ✅ Inherited |
| Human + CI parallel execution | WAITING state — total time = max, not sum | ✅ Layer 5 |
| Observability + operational tooling | 16 MCP tools (11 read + 5 write) + W3C PROV-DM + event-sourced tracker | ✅ devtown#17 |
| Merge queue | CasePlanModel batch-then-bisect with 3 bisection strategies + adaptive sizing | ✅ devtown#11 |
| Merge queue webhook admission | GitHub labeled events enqueue PRs | ✅ devtown#101 |
| Adaptive batch sizing | Batch size adapts to failure rate | ✅ devtown#103 |
| PR governance workbench | Dashboard for case lifecycle, supersede/relink | ✅ devtown#85 |
| GitHub integration | CI status flow, merge execution | Partial — webhooks shipped (#101), CI/merge pending |

### 4.3 Gastown Feature Parity Checklist

| Gastown feature | devtown approach | Status |
|----------------|-----------------|--------|
| Merge queue (Bors batch-then-bisect) | CasePlanModel + recursive bisect sub-cases + 3 bisection strategies + adaptive batch sizing | ✅ Shipped (devtown#11, #101, #103) |
| AI coding agent workers | Claudony WorkerProvisioner | Foundation ready |
| Human workspaces (Crew) | Human review WorkItem via casehub-work | ✅ Layer 2 |
| Cross-rig agent routing | Sub-case orchestration | Foundation ready |
| CLI tooling | 16 MCP tools (11 read + 5 write) — AI-native, protocol-native | ✅ devtown#17 |
| Predecessor session context | `get_prior_decisions` MCP tool + CaseMemoryStore | ✅ Partial (full requires Doltgres P1.5) |
| Federated reputation (Wasteland) | TrustExportService + TrustImportService | ✅ Shipped |
| Sandboxed execution | gt-proxy-server equivalent | Not planned |
| Agent concurrency control | SpawnThrottle in ClaudonyConfig | Not started |
| Hierarchical watchdog | RecoveryPolicy SPI | Not started |

---

## 5. Where CaseHub Surpasses Gastown

Capabilities that require a structural rewrite in Gastown to match — not bolt-on features.

### 5.1 The Closed Feedback Loop

The prescriptive->normative->evaluative->prescriptive cycle is live. Trust scores update from agent behaviour, driving future routing, without human intervention. Gastown cannot close this loop because it has no normative layer to generate the evaluative signal. Adding status fields to beads gives you tracking, not accountability — and accountability semantics are what trust scoring requires to be meaningful.

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

The casehub-blocks consolidation (8 blocks across blocks#12-#30) proves this at a finer grain: trust routing configuration, oversight gate infrastructure, debate channels, AI routing strategies, accountability listeners, and OTel metrics are all shared across domains without modification. When devtown needed conversation protocols, they were extracted to a block. When aml needed the same oversight gates, they were already there. The pattern is: build it in one domain, extract it to a block, share it across all domains.

### 5.8 AI-Native Operational Tooling

16 MCP tools provide the operational surface via protocol — not shell commands. An AI agent can monitor the review queue (`get_queue_status`), detect a stalled reviewer (`list_problems`), check trust scores (`get_reviewer_health`), trigger a retry (`retry_reviewer`), and export the provenance chain (`export_prov`) — all through the same MCP protocol it uses for its own work. Five merge queue tools shipped with Epic #11, covering queue admission, batch status, and bisection progress. Gastown's `gt` CLI is shell-only: agents are workers in Gastown, never operators. CaseHub's MCP surface means agents can self-monitor the system they participate in, closing the loop between coordination and observability.

### 5.9 Merge Queue — Ten Architectural Differences

The merge queue (devtown#11) is feature-complete. CaseHub's design takes a structurally different approach — a CasePlanModel where the merge strategy is binding conditions, not compiled code. The architecture is two-tier: an imperative `MergeQueueService` for queue management (priority, dependency DAG, batch formation) and a reactive `merge-batch` CasePlanModel for batch processing (test, bisect, merge, reject).

Each capability below reflects a structural architectural difference between the traditional Bors-style approach and CaseHub's CasePlanModel-based design:

| # | Capability | Traditional (Bors-style) | CaseHub |
|---|-----------|-------------------------|---------|
| 1 | Strategy as data | Compiled code | CasePlanModel YAML, per repo at runtime |
| 2 | Trust-weighted batch composition | FIFO batching | Batch size proportional to min trust; low-trust PRs get smaller batches |
| 3 | Trust-weighted bisection | Positional binary split | Split by trust — isolate likely culprits first |
| 4 | Priority lanes with starvation prevention | FIFO ordering | Composite score with time-decay (125 pts/hr default) |
| 5 | Dependency-aware ordering | Author-managed | DAG from labels + git base-branch |
| 6 | SLA-bounded queue wait | Not tracked | WorkItem per queued PR, tiered escalation |
| 7 | Adaptive batch sizing | Static configuration | Batch size adapts to recent failure rate (floor of 1) — devtown#103 |
| 8 | Cryptographic merge audit | Application logs | MergeDecisionLedgerEntry in Merkle chain |
| 9 | Human oversight for high-risk merges | Uniform merge path | ActionRiskClassifier gates by risk level |
| 10 | Recursive auditable bisection | Internal to the algorithm | Every level in EventLog with causal chain |

**Trust-weighted bisection** is the headline. Traditional merge queues bisect mechanically — split by position, no information about which half contains the culprit. CaseHub sorts PRs by trust score before splitting, clustering low-trust PRs (statistically more likely to fail) in one sub-batch. A pluggable `BisectionSplitStrategy` SPI supports three implementations: `TrustWeightedSplitStrategy` (default), `IsolateOutlierStrategy` (>2sigma outlier isolation), `BinarySplitStrategy` (positional, for benchmarking).

**Recursive sub-cases** enable the CasePlanModel to bisect by spawning two sub-batch cases using the same model definition. Each level of bisection is a full case with its own EventLog entries, trust-weighted agent selection, and human escalation path. The bisection tree is auditable end-to-end via `get_causal_chain`. Foundation gate closed: engine#573.

**M-of-N grouped sub-cases** enable parallel bisection — both halves test simultaneously, parent waits for both via `groupId` with `requiredCount: 2`. Each child writes to a separate output key (`bisectLeft`/`bisectRight`). Foundation gate closed: engine#574.

**Adaptive batch sizing** (devtown#103) makes the merge queue self-tuning. Batch size adjusts to the observed failure rate: when failures are frequent, batches shrink to reduce bisection overhead; when the pipeline is stable, batches grow to maximise throughput. Floor of 1 ensures forward progress.

**GitHub webhook admission** (devtown#101) connects the merge queue to real GitHub events. PRs are enqueued when specific labels are applied, removing the need for manual REST calls.

### 5.10 Trust Pipeline — Self-Reinforcing Confidence

TrustGatedAttestationPolicy (engine#668, devtown#97) adds a dimension Gastown has no path to: the trust signal is self-reinforcing.

When an agent completes work (DONE), the system writes a LedgerAttestation. The attestation's confidence is now modulated by the attesting agent's own trust tier:

| Trust tier | Confidence | Effect |
|-----------|------------|--------|
| BOOTSTRAP (Phase 0) | 0.7 | New agents produce moderate signals — enough to start building history but not enough to dominate |
| QUALIFIED (Phase 2+) | Boosted (above 0.7) | Proven agents produce stronger attestations, compounding faster in Bayesian scoring |
| BORDERLINE | 0.7 | Agents near the threshold produce attenuated signals, requiring more observations before influencing routing |
| BELOW_THRESHOLD | Scaled down (floor 0.05) | Untrusted agents' attestations are nearly discounted — they cannot inflate their own or others' scores |

The practical effect: a fleet of proven agents converges faster. A single bad agent cannot pollute the trust pool. An agent that crosses from BORDERLINE to QUALIFIED sees its subsequent attestations carry more weight, accelerating trust convergence for the agents it interacts with.

Gastown has no trust pipeline at all — stamps are static, human-curated labels that do not change based on agent behaviour.

---

## 6. Where Gastown Still Leads

Not minimised. These are real operational advantages.

### 6.1 Operational Maturity

Gastown is v1.0.1, in production, with a known failure profile and operational tooling built from experience. CaseHub is pre-production. This is the most significant single fact.

### 6.2 Hierarchical Recovery

Witness monitors per-rig polecats. Deacon monitors cross-rig. Boot validates Deacon every 5 minutes. Each tier detects failure and takes action — re-assignment, restart, infrastructure validation. CaseHub has detection (WatchdogEvaluationService, `list_stalled_obligations`) but no automated recovery action. The `RecoveryPolicy` SPI is designed but not implemented. At 20+ agents, manual recovery is operationally unsustainable. This is CaseHub's most significant gap.

### 6.3 Concurrency Control

Gastown's Scheduler prevents Claude API rate limit exhaustion at the session level. CaseHub's WorkerProvisioner spawns sessions without throttle. At 10+ concurrent cases this becomes a hard failure, not a degradation.

### 6.4 The Pattern

Every area where Gastown leads is **operational** — things needed to run at scale. Every area where CaseHub leads is **structural** — things that cannot be bolted on after the fact. Gastown's advantages can be built on CaseHub's foundation. CaseHub's advantages require Gastown to rebuild its foundation.

The remaining Gastown advantages are: production maturity (S6.1), hierarchical recovery automation (S6.2), and concurrency control (S6.3). Tooling parity was reached with Epic 10 and has since been extended by 5 merge queue MCP tools.

---

## 7. Remaining Roadmap

### 7.1 Foundation Gaps

| Gap | What it is | Priority | Impact |
|-----|-----------|----------|--------|
| P1.1 Concurrency throttle | `SpawnThrottle` in ClaudonyConfig — cap concurrent worker sessions | High | Hard failure at 10+ concurrent cases |
| P1.2 Recovery automation | `RecoveryPolicy` SPI — REPROVISION, ESCALATE_TO_HUMAN, CANCEL_CASE | High | Manual recovery unsustainable at 20+ agents |
| P1.5 Doltgres backend | Configurable `casehub.ledger.backend=doltgres` — time-travel, branching | Low | Nice-to-have for debugging; not blocking |

### 7.2 Application Epics

| Epic | What it is | Scale | Status |
|------|-----------|-------|--------|
| ~~#11 Merge queue~~ | ~~CasePlanModel batch-then-bisect with trust-weighted bisection~~ | ~~XL~~ | ✅ Shipped |
| ~~#17 Operational tooling~~ | ~~MCP tools: queue status, reviewer health, merge audit~~ | ~~XL~~ | ✅ Shipped |
| #12 Cross-repo coordinated merge | Parent case + per-repo sub-cases | XL | Blocked by #11 ✅ — ready to start |
| #13 Trust-weighted reviewer routing (full) | PostMergeIncidentHandler + full trust feedback wiring | XL | Layer 6 ✅ |
| #14 Failure handling | DECLINED vs FAILED declarative routing | XL | P0 ✅ |
| #15 GitHub integration | CI status flow, merge execution | XL | Partial — webhooks shipped (#101) |
| #16 Notification wiring | casehub-connectors integration | XL | parent#5 ✅ |
| #129 Case-Based Reasoning | CBR-enhanced routing: similarity model, retrieval, capability activation, reviewer matching, batch risk, bisection heuristics, SLA calibration | XL | Filed — 12 sub-issues (#130-#138) |

### 7.3 Follow-on Work

| Issue | What it is | Priority |
|-------|-----------|----------|
| devtown#141 | EvidentialChecker V1-V4 integration with TrustGatedAttestationPolicy | Medium |
| devtown#139 | SNAPSHOT drift blocking full test suite | High |

### 7.4 Where the Roadmap Leaves Us

**#11 (merge queue) and #17 (operational tooling) are shipped.** The core Gastown application feature — batch-then-bisect merge queue — is now matched with ten architectural improvements. 16 MCP tools provide operational parity plus AI-native additions.

**#129 (Case-Based Reasoning) is the next phase.** CBR-enhanced routing uses historical case similarity to improve capability activation, reviewer matching, batch risk scoring, bisection heuristics, and SLA calibration. Twelve sub-issues (#130-#138) cover the full pipeline from similarity model to memory browser.

**After P1.1 + P1.2:** CaseHub can run at agent scale. Gastown's two remaining operational advantages close.

**After #15 (GitHub integration complete):** CaseHub connects to real PR events end-to-end. Webhooks are shipped (#101). CI status flow and merge execution via GitHub API are pending.

At that point, CaseHub has Gastown parity on application features plus structural advantages Gastown cannot match. The remaining gap is operational maturity — which only comes from running in production.

---

## 8. Critical Path to Demo

The merge queue is demoable. Three demo paths — architectural depth for engineers, governance value for managers, AI-native operations for both — plus a fourth path showing the merge queue.

### What exists today

All of the following are shipped, tested, and wired:

**Layers 1-6 (core coordination):**
- CasePlanModel with JQ binding conditions (Layer 5)
- Content-driven routing — bindings fire on code analysis results (Layer 5)
- TrustWeightedAgentStrategy driving agent selection (Layer 6)
- TrustGatedAttestationPolicy modulating attestation confidence (devtown#97)
- Qhorus COMMAND/DONE commitment lifecycle (Layer 3)
- LedgerAttestation from commitment outcomes -> TrustScoreJob (P0.2)
- WorkItem human review gate with SLA + two-tier escalation (Layer 2)
- MergeDecisionLedgerEntry + ComplianceSupplement (Layer 4)
- HITL end-to-end — happy path and escalation (devtown#33)
- PR review REST resource for case creation

**Merge queue (Epic #11):**
- Batch-then-bisect CasePlanModel
- Three bisection strategies (TrustWeightedSplitStrategy, IsolateOutlierStrategy, BinarySplitStrategy)
- Trust-weighted batch composition
- Adaptive batch sizing from failure rate (devtown#103)
- SLA per queued PR
- GitHub webhook admission (devtown#101)
- Recursive sub-cases (engine#573) + M-of-N grouped sub-cases (engine#574)

**Cross-cutting capabilities:**
- ActionRiskClassifier oversight gate — 8 action types, 4 categories (devtown#56)
- Compliance report endpoint — 4 regulatory dimensions (devtown#7)
- GDPR erasure endpoint with tamper-evident receipt (devtown#74)
- Post-merge trust feedback — FLAGGED attestation on incident-linked review (devtown#5)

**Governance and UI:**
- PR governance workbench — case lifecycle dashboard (devtown#85)
- casehub-pages Quinoa integration (devtown#92)

**Observability and operations (Epic 10, devtown#17):**
- 16 MCP tools — 11 read + 5 write
- `PrReviewCaseTracker` — event-sourced read model with ring buffer
- W3C PROV-DM export via `LedgerProvExportService`

### What needs to be built

**Step 1: Mock lambda workers** — S, Low

Three CDI `@ApplicationScoped` lambda workers that write to the case blackboard:

- `MockCodeAnalysisWorker` — reads PR metadata, writes `{analysisComplete: true, securitySensitive: true/false}` based on file paths
- `MockSecurityReviewWorker` — writes `{securityCheck: {passed: true, findings: [...]}}` after a short delay
- `MockCiRunnerWorker` — writes `{ci: {status: "passing"}}` after a delay

These simulate the worker output shape so binding conditions fire correctly. Each is ~20 lines.

**Step 2: Trust score seeding** — XS, Low

A `DemoDataSeeder @Startup` bean (activated via a `demo` Quarkus profile) that:

- Registers 3 mock agents with `ActorTrustScore` rows at different capability-scoped scores
- Sets agent A above the security-review threshold (0.82), agent B below but close (0.61), agent C with no observations (Phase 0 bootstrap)
- Gives agent B enough history to cross the threshold after one more positive outcome
- Seeds CaseMemoryStore with prior review decisions for `src/auth/` path

**Step 3: Demo scenario script** — S, Low

An `.http` file (IntelliJ HTTP Client format) with annotated sections for each demo path. Each section includes the REST call followed by the MCP tool query that reveals what happened underneath.

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
| 6 | LedgerAttestation (SOUND, confidence boosted by trust tier) -> TrustScoreJob runs | `get_reviewer_health` for agent B — score now crosses threshold |
| 7 | Submit second PR for `src/auth/SessionManager.java` | `get_queue_status` — second case appears |
| 8 | Trust-weighted routing now selects agent B | `get_reviewer_health` for agent B — open commitment appears |
| 9 | Export provenance chain | `export_prov` — W3C PROV-DM shows full causal chain |

**What this proves:** No configuration change. No human intervention. The routing improved by operating. Agent B earned its way from Phase 0 to Phase 1 through demonstrated competence. TrustGatedAttestationPolicy accelerated the convergence because agent A's high trust score produced a boosted attestation.

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

**Path D — Merge Queue** (platform engineers)

The core Gastown feature, rebuilt with ten architectural improvements.

| Step | Action | Observe via |
|------|--------|------------|
| 1 | Enqueue 4 PRs via webhook (labeled events) | Merge queue MCP tools — 4 entries queued |
| 2 | Batch formation — trust-weighted composition | Batch size reflects minimum trust in cohort |
| 3 | Tip-of-batch CI test | `inspect_review` — CI binding fires on batch case |
| 4 | CI fails — bisection triggers | Two sub-cases spawn (engine#573), parallel (engine#574) |
| 5 | TrustWeightedSplitStrategy clusters low-trust PRs | Low-trust half tested first — culprit found faster |
| 6 | Faulty PR identified, rejected | `export_prov` — full bisection tree in causal chain |
| 7 | Remainder retested and merged | MergeDecisionLedgerEntry in Merkle chain |

**What this proves:** The merge strategy is data (YAML), not compiled code. Trust scores drive bisection, not position. Every bisection level is a full case with its own audit trail. Adaptive batch sizing means the next batch adjusts to the failure. Gastown's Refinery is compiled Go code with positional splits and no audit trail.

### What's not in the demo

| Capability | Why not | When |
|-----------|---------|------|
| Complete GitHub integration | CI status flow and merge execution pending — webhooks work | Epic #15 |
| Real AI agent review | Claudony + Claude CLI required — mock workers demonstrate the same routing and commitment semantics | Can layer in later |
| Doltgres time-travel | P1.5 — `get_prior_decisions` works via CaseMemoryStore without Doltgres | Not planned for demo |
| CBR-enhanced routing | Epic #129 filed — similarity model and retrieval service not yet built | Post-demo |

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
| Interface | `gt` CLI (comprehensive) | 16 MCP tools + REST APIs + governance workbench |
| Extension model | Plugin system (5 gate types) | SPI-based (compile-time verified) |
| Ecosystem | Go stdlib ~27 deps (closed) | Full Quarkiverse |
| Foundation reuse | Not applicable (single app) | 8 reusable blocks across 6+ domains |
| Version | v1.0.1 (production) | Pre-production (6 layers + merge queue shipped) |

---

## 10. Foundation Hardening (July 2026)

Significant foundation stabilisation work completed across four repos in preparation for merge queue and production readiness:

**casehub-qhorus:** 9 fixes in 2 days (July 4-6) — race condition fixes, CDI ambiguity resolution, reactive parity between imperative and reactive paths.

**casehub-work:** Category-to-types migration (`Set<Path>` with ancestor matching), REST module extraction, template versioning, CloudEvent bridge.

**casehub-ledger:** Vault auth SPI consolidation (AppRole, Kubernetes, JWT), Cloud KMS adapters (AWS, GCP, Azure, Vault Transit), REST module extraction.

**casehub-platform:** Notification system complete (SubscriptionStore, SubscriptionEngine, digest batching), `NamedStrategy` + `StrategyResolver` extracted to platform-api.

**casehub-engine:** PlanItem CAS loop fixes, dual-stack repository naming, CaseDefinition types/labels classification.

This hardening is not visible in the application feature list but is load-bearing for production deployment. Nine qhorus fixes in 48 hours is the kind of foundation stress-testing that only happens when a real application (the merge queue) exercises every path.
