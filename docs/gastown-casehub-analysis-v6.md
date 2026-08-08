# CaseHub vs Gas City: Architectural Analysis v6

> **Date:** 2026-08-07
> **Previous versions:** [v5](gastown-casehub-analysis-v5.md) (2026-07-07), [v4](gastown-casehub-analysis-v4.md) (2026-06-26), [v3](gastown-casehub-analysis-v3.md) (2026-06-18), [v2](gastown-casehub-analysis-v2.md) (2026-05-25), [v1](gastown-casehub-analysis.md) (2026-04-27, archived)

---

## Competitive Position Scoreboard

Position: who leads. Gap: how significant. Trend: direction since v5 (Gas Town → Gas City, CaseHub July → August).

### Structural Dimensions (CaseHub leads)

| Dimension | CaseHub | Gas City | Gap | Trend | Blocker / Epic |
|-----------|---------|---------|-----|-------|---------------|
| Coordination model | ACM + blackboard | Workflow (formulas v2) | Large | Stable | — |
| Normative accountability | 9 speech-act types, 7-state commitment | MEOW lifecycle + GUPP | Large | Narrowing (MEOW is better than nudge/sling) | — |
| Trust pipeline | Bayesian Beta + EigenTrust, closed feedback loop | Wasteland multi-dimensional stamps (human-curated) | Large | Narrowing (Wasteland is better than flat stamps) | — |
| Routing sophistication | 4-layer: trust → semantic → LLM → CBR | Prefix + metadata + formula run_target | Very large | Widening (CBR, eidos new since v5) | — |
| Agent identity | eidos: 4-layer descriptors, health probing, behavioral signals, vocabulary | Role name in pack config | Very large | Widening (eidos new since v5) | — |
| Compliance depth | Merkle + GDPR + EU AI Act + 6-framework ops | Dolt audit (SOC2 narrative) | Large | Narrowing (Dolt > nothing) | — |
| Content-driven routing | Binding conditions on code analysis | Structural metadata only | Large | Stable | — |
| AI-native operations | 16 MCP tools (AI-as-operator) | gc CLI (shell-only) | Large | Stable | — |
| Merge queue architecture | CasePlanModel + trust-weighted bisection + adaptive sizing | Bors batch-then-bisect (positional) | Large | Stable | — |
| Multi-domain proof | 8 app repos (regulated, consumer, entertainment) | Architecturally supported; shipped packs coding-only | Large | Widening (soc, fsitrading added) | — |
| Orchestration patterns | blocks: supervisor, sequence, loop, parallel, voting, debate, HTN | Formulas v2 with drain/convergence | Medium | Widening (blocks new since v5) | — |
| On-device inference | ONNX + SPLADE + CRAG + RAG fusion | None | Very large | Widening (neocortex new since v5) | — |
| CBR (learn from past cases) | 4-step pipeline, active in 6+ apps | None | Very large | Widening (CBR new since v5) | — |

### Operational Dimensions (Gas City leads)

| Dimension | Gas City | CaseHub | Gap | Trend | Blocker / Epic |
|-----------|---------|---------|-----|-------|---------------|
| Production maturity | v1.0, hundreds of agents, 74 PRs/day | Pre-production | Very large | Stable | Needs production deployment |
| Recovery automation | Patrol self-healing + NDI + crash loop quarantine | Detection only; RecoveryPolicy SPI not implemented | Large | Widening (NDI is deeper than Witness/Deacon) | #185 |
| Concurrency control | Pool scaling, max/min sessions, circuit breaker | SpawnThrottle not started | Large | Widening (Gas City more mature than Gas Town) | #185 |
| Agent ecosystem breadth | 9 CLI agents + 7 runtime providers | Claude + LangChain4j bridge + OpenClaw | Medium | Widening (Gas City added Amp, Pi, Grok, Groq, Cerebras) | #186 |
| Template/pack ecosystem | gascity-packs repo, Git URL import, hierarchical override | CasePlanModel YAML per app, no sharing mechanism | Medium | New gap (packs didn't exist in Gas Town) | #187 |
| Federated reputation at scale | Wasteland connecting thousands of instances | TrustExport/Import shipped, not deployed | Medium | New gap (Wasteland is new) | — |

### Parity / Contested

| Dimension | Position | Notes |
|-----------|---------|-------|
| Multi-lane review | Parity | Different mechanisms (formula fan-out vs binding conditions), same outcome |
| Stalled agent detection | Parity | Patrols vs WatchdogEvaluationService — Gas City has recovery, CaseHub does not |
| Multi-domain architecture | CaseHub proven, Gas City supported | Both are domain-agnostic; CaseHub has 8 apps, Gas City has coding packs |
| Durable work persistence | Parity | MEOW beads in Git vs CasePlanModel + PlanItemStore + engine blackboard |
| Secret management | CaseHub leads | Vault + Cloud KMS vs environment stripping |

### Reading the scoreboard

- **Gap = Very large:** Fundamental architectural difference. Would require ground-up rebuild to close.
- **Gap = Large:** Significant capability difference. Addressable but requires substantial engineering.
- **Gap = Medium:** Feature gap. Closable with focused effort.
- **Trend = Widening:** The leader is pulling further ahead (either through new capability or the other side not investing).
- **Trend = Narrowing:** The trailer is closing the gap (Gas City improved over Gas Town, or CaseHub addressed a gap).
- **Trend = New gap:** Capability that didn't exist in the previous comparison.

### Summary position

CaseHub holds 13 structural advantages (4 very large, 7 large, 2 medium). Gas City holds 6 operational advantages (1 very large, 2 large, 3 medium). Since v5, CaseHub's structural lead has widened in 6 dimensions (routing, agent identity, orchestration patterns, multi-domain, inference, CBR). Gas City's operational lead has widened in 3 dimensions (recovery/NDI, concurrency, agent breadth) and introduced 2 new gaps (packs, Wasteland federation).

Three epics (#185, #186, #187) target the operational gaps. After those close, the competitive picture reduces to production maturity — which only comes from running in production.

---

## 1. Executive Summary

Gas City (April 2026) replaces Gas Town as the comparison target. Built by Julian Knutsen and Chris Sells, it decomposes Gas Town's monolithic orchestrator into an SDK of six primitives (Agent, Bead, Formula, Rig, Pack, Event). Gas Town is in maintenance mode; Yegge directs users to Gas City.

Gas City closes several gaps Gas Town had against CaseHub — trust is now multi-dimensional and evidence-anchored (Wasteland), the MEOW stack provides real work lifecycle tracking, Dolt-based audit gives a SOC2 narrative, and the pack model supports multi-domain use. Operationally, Gas City has proven scale at hundreds of concurrent agents with patrol-based self-healing and crash loop protection.

CaseHub has also advanced substantially since v5. casehub-eidos provides structured 4-layer agent identity with capability health probing, behavioral signal accumulation, and system prompt generation. casehub-neocortex delivers full CBR (Case-Based Reasoning) with typed feature similarity, trend detection, plan adaptation, and CRAG. casehub-blocks ships a composable orchestration pattern library (supervisor, sequence, loop, parallel, voting, debate, conditional, HTN). casehub-ops provides infrastructure desired-state provisioning and continuous compliance across 6 frameworks. casehub-workers supports 6 dispatch types. The application tier has grown to 8 repos. casehub-openclaw provides bidirectional CaseHub-to-OpenClaw accountability. The platform now has ACL, 5 external event ingestion modules, a unified ChatPlatform SPI (Slack + Discord), and a pages push protocol with typed wire format.

The v5 pattern — "every area where Gastown leads is operational; every area where CaseHub leads is structural" — still holds but needs refinement. Gas City's operational advantages are now deeper (patrol-based NDI, Factory Worker API, 7 runtime providers). But CaseHub's structural distance has widened in areas Gas City is not pursuing: agent identity, CBR, 4-layer routing, composable orchestration patterns, continuous compliance, and on-device neural inference. Gas City's trust system is richer than Gas Town's but still human-curated stamps that don't drive routing; CaseHub's is Bayesian-computed and routing-integrated. Gas City's compliance story is Dolt-based (admin-trusted); CaseHub's is Merkle-based (independently verifiable).

CaseHub surpasses Gas City on: coordination model (ACM vs workflow), normative accountability (9 speech-act types vs MEOW lifecycle), trust pipeline depth (Bayesian + EigenTrust vs curated stamps), compliance (Merkle + GDPR + EU AI Act vs Dolt audit), routing sophistication (4-layer trust/semantic/CBR/LLM vs prefix/metadata), agent identity (eidos 4-layer descriptors vs role names), AI-native operations (MCP tools for AI-as-operator vs shell CLI), merge queue architecture (CasePlanModel with trust-weighted bisection vs Bors), composable orchestration patterns (blocks pattern library vs formula templates), continuous compliance (6-framework ops module vs SOC2 narrative), multi-domain proof (8 application repos vs coding-only packs), and content-driven routing (binding conditions on code analysis vs structural metadata).

Gas City leads on: operational maturity (production at hundreds of agents vs pre-production), recovery automation (patrol-based self-healing + NDI vs unimplemented RecoveryPolicy SPI), concurrency control (pool scaling + crash loop protection vs unimplemented SpawnThrottle), agent ecosystem breadth (9 CLI agents + 7 runtime providers vs primarily Claude), pack ecosystem (shareable workflow templates vs application-specific CasePlanModel definitions), and federated reputation at scale (Wasteland connecting thousands of instances vs shipped-but-undeployed TrustExport/Import).

---

## 2. What Changed: Gastown to Gas City

Gas City is not a minor version bump. It is a ground-up rewrite of the orchestration layer.

| Dimension | Gas Town | Gas City |
|-----------|---------|---------|
| Architecture | Monolithic orchestrator with hardcoded roles (Mayor, Deacon, Polecats, Refinery, Witness, Crew, Dogs, Boot) | SDK of 6 primitives (Agent, Bead, Formula, Rig, Pack, Event); zero hardcoded roles |
| Composition | Plugin system | Pack model — import, override, compose via `city.toml` and `pack.toml` |
| Trust | Human-curated stamps | Wasteland: multi-dimensional stamps (quality/reliability/creativity), evidence-anchored, anti-fraud topology detection, federated via DoltHub |
| Normative | Nudge + sling (2 informal) | MEOW stack: Bead→Epic→Molecule→Protomolecule→Formula. GUPP obligation principle. Wasteland 4-stage commitment (Open→Claimed→In Review→Completed) |
| Compliance | None | Dolt-based audit trail (SQL-queryable, git-versioned), 4-tier trust authority model, secret propagation model |
| Scale | 20-30 agents | Hundreds of concurrent agents proven |
| Formulas | v1 (static TOML) | v2: orchestrator-driven execution, control flow constructs (check/retry/drain/tally), multi-lane review, convergence loops |
| Recovery | Witness/Deacon/Boot hierarchy | Patrol-based self-healing, NDI (nondeterministic idempotence), crash loop quarantine |
| Extensibility | Go plugin system (5 gate types) | Factory Worker API + 7 runtime providers (tmux, subprocess, exec, ACP, K8s, hybrid, herdr) |
| Multi-domain | Single application | MEOW is domain-agnostic; rig-based multi-tenancy. Shipped packs still coding-only |
| Agent support | Claude, Copilot, Gemini, Cursor, Codex | Claude, Codex, Gemini, Amp, OpenCode, Pi, Grok Build, Groq, Cerebras |
| Protocol | Proprietary | ACP (Agent Client Protocol): JSON-RPC over stdio for agent-to-orchestrator communication |
| HITL | Bead on hook (same as agent) | Formula-based gates, convergence loops with check scripts, interactive session attach/peek |

The pack model is the headline architectural change. All Gas Town roles — Mayor, Deacon, Polecats, Refinery, Witness — become pack configuration rather than platform types. This makes Gas City more composable than Gas Town, but the composition unit is workflow templates (what to do), not coordination primitives (how to decide).

---

## 3. Current State — What CaseHub Has Built

### 3.1 Foundation Layers (all shipped)

Unchanged from v5. Six layers: domain baseline, casehub-work, casehub-qhorus, casehub-ledger, casehub-engine, trust routing.

### 3.2 Merge Queue (Epic #11 — shipped)

Unchanged from v5. Batch-then-bisect CasePlanModel with three bisection strategies, trust-weighted batch composition, adaptive batch sizing, SLA per queued PR, GitHub webhook admission, recursive sub-cases, M-of-N grouped sub-cases.

### 3.3 Governance and UI

Unchanged from v5. PR governance workbench + casehub-pages Quinoa integration.

### 3.4 Trust Pipeline

Unchanged from v5. TrustGatedAttestationPolicy (engine#668, devtown#97).

### 3.5 Additional Shipped Capabilities

Unchanged from v5. GDPR, ActionRiskClassifier, compliance report, post-merge trust feedback, HITL, observability + MCP tools.

### 3.6 casehub-blocks — Foundation Consolidation

Expanded since v5. Eight reusable blocks plus a composable orchestration pattern library:

**Orchestration patterns (new):** Sealed types for routing, decomposition, activation, aggregation, and termination. `OrchestratedDriver` (centralized control) + `ChoreographedDriver` (peer-to-peer). Pattern builders: `supervisor`, `sequence`, `loop`, `parallel`, `voting`, `debate`, `conditional`, `HTN` with fluent DSL. `ExecutionPlan` DAG type with dependency edges. Hybrid decomposition: static-first, LLM fallback (ChatHTN-style).

**Trust routing, conversation management, oversight gates, AI routing, accountability listeners, OTel metrics, RAG-enriched routing, inter-agent channels** — all shared across 6+ domains.

### 3.7 New Platform Capabilities (since v5)

| Capability | Owner | What it provides |
|-----------|-------|-----------------|
| Agent identity (4-layer descriptors) | casehub-eidos | `AgentDescriptor` (identity/slot/capabilities/disposition), `CapabilityHealth` probing, `BehavioralSignalStore` (learned routing + compliance), `VocabularyRegistry` (Belbin, DISC, Thomas-Kilmann), `SystemPromptRenderer` (MARKDOWN/PROSE/A2A_CARD), `AgentDescriptorRegistrar` (YAML-driven) |
| CBR (Case-Based Reasoning) | casehub-neocortex + engine + blocks | 4-step pipeline: Retrieve (hybrid semantic+metadata, typed `FeatureValue` sealed hierarchy, DTW for temporal, trend detection), Reuse (`PlanAdapter` SPI + `AdaptedPlan`), Revise (outcome feedback via `RoutingOutcomeRecorder`), Retain (`CaseMemoryStore` + ledger). Active in devtown, aml, clinical, life, iot, desiredstate |
| 4-layer routing | ledger + blocks + engine | Score computation → policy config → classical strategies (trust-weighted, semantic) → AI strategies (LLM-reasoned, CBR-evidence). `TrustCandidateClassifier` with 5 classifications. Composable prompt enrichment via `RoutingPromptSection` SPI |
| ONNX neural inference | casehub-neocortex | `inference-tasks` — typed adapters for NLI, classification, regression, reranking. SPLADE sparse embeddings. CRAG (corrective RAG). Configurable fusion (RRF/DBSF/CC) |
| Worker dispatch (6 types) | casehub-workers | HTTP, Camel (300+ connectors), GitHub Actions, MCP, Kubernetes, Script. Worker runtime lifecycle, capability discovery |
| OpenClaw integration | casehub-openclaw | Bidirectional: CaseHub dispatches to OpenClaw agents via `DirectCallBridge`; OpenClaw agents call CaseHub accountability MCP tools (`casehub_commit`, `casehub_done`, `casehub_reject`, `casehub_checkpoint`, `casehub_escalate`). `OpenClawAgentProvider` implements `AgentProvider` SPI |
| Infrastructure desired-state | casehub-ops | `InfraGoalCompiler` + `DeploymentGoalCompiler`. Three modes: standalone, Terraform augmentation, Ansible augmentation. Endpoint provisioning + drift detection |
| Continuous compliance | casehub-ops | Six frameworks (SOC2, GDPR, EU AI Act, DORA, NIS2, ISO27001). Evidence-based drift detection via `EvidenceCollector` SPI → `ComplianceLedgerEntry` (tamper-evident). Five-category posture scoring |
| Platform streams | casehub-platform | Five external event ingestion modules: Kafka, AMQP, webhook, poll, Camel. All fire `Event<CloudEvent>.fireAsync()` |
| ACL | casehub-platform | Resource-level access control with group-based grants, parent inheritance (depth guard 20), tenant-filtered queries. JPA + in-memory backends |
| ChatPlatform SPI | casehub-connectors | Unified multi-platform chat abstraction. Implementations: Slack (9 capabilities), Discord (8 capabilities). `RichCard` cross-platform rich message format. Unified `send_chat` MCP tool |
| Agent mesh | casehub-engine-api | 3-channel normative layout (work/observe/oversight), mesh participation strategies (ACTIVE/REACTIVE/SILENT), `CaseChannelLayout` SPI |
| Multi-turn agent sessions | casehub-platform | `AgentSession` interface — serial query/interrupt/close. `ClaudeAgentSession` IDLE/ACTIVE/CLOSED state machine. `LangChain4j` bridge (`ChatModelAgentProvider` + `AgentProviderChatModel`) |
| Pages push protocol | casehub-pages | `PushMessage` builders, `TopicRegistry` (wildcard-aware), `EventStore` SPI + `InMemoryEventStore` (bounded ring buffer, per-topic seq, event replay). Wire protocol correlation |
| 8 application repos | Application tier | devtown, aml, clinical, life, drafthouse, quarkmind, soc (scaffold), fsitrading (scaffold) |

### 3.8 The Closed Feedback Loop

Unchanged from v5. Prescriptive → normative → evaluative → prescriptive cycle is live. TrustGatedAttestationPolicy adds compounding effect.

---

## 4. Foundation vs Foundation

### 4.1 Coordination Model

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Paradigm | Workflow (formula steps, v2 adds control flow constructs) | ACM (goals, emergent paths via blackboard) | **CaseHub** — ACM is the correct paradigm for agent coordination where outputs determine next steps. Gas City's v2 formulas add iteration and branching but the paradigm is still step-driven |
| Driving force | Patrol-based reconciliation loop (desired-state → running-state) | Context change fires binding evaluation | **CaseHub** — engine-initiated, zero-latency. Gas City's patrol model is self-healing but poll-based with exponential backoff |
| Parallelism | Formula drain fan-out + convoy separation | Automatic — all bindings whose conditions are simultaneously satisfied fire at once | **CaseHub** — adding a new check type is one binding, not restructuring the formula |
| Failure handling | NDI (nondeterministic idempotence) — "keep throwing agents at it" | Engine re-evaluates bindings with updated context; alternative paths fire automatically | **Both strong, different philosophy** — NDI is pragmatically powerful for LLM flakiness; ACM failure is new information that routes to alternative paths |
| Composition unit | Packs (import, override, compose) | CasePlanModel YAML + blocks pattern library (supervisor, sequence, loop, parallel, voting, debate, conditional, HTN) | **Different level** — packs compose workflow templates; blocks compose coordination patterns. CaseHub is more general; Gas City is more immediately usable |
| Multi-lane review | Formula-based: fan-out to N reviewer roles → synthesizer → optional iterate | CasePlanModel bindings fire N checks simultaneously; synthesizer is another binding | **Parity** — both achieve multi-lane review. CaseHub's binding-based approach is more declarative |
| Hybrid modes | "High-control system with high parallelism" — structured workflow + unstructured "tell polecats to solve a problem" | Choreography + orchestration per case, no pre-commitment | **CaseHub** — fan out reactively, suspend for human decision, resume; single case, no mode switch |

### 4.2 Normative Layer

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Communication types | MEOW lifecycle (5 layers) + mail system | 9 speech-act types (Searle taxonomy) | **CaseHub** — provably complete classification; no 10th type to discover. MEOW gives lifecycle, not illocutionary taxonomy |
| Obligation tracking | GUPP ("if there is work on your hook, YOU MUST RUN IT") + Wasteland 4-stage (Open→Claimed→In Review→Completed) | 7-state Commitment lifecycle (OPEN→FULFILLED/FAILED/DECLINED/EXPIRED/WITHDRAWN/DELEGATED) | **CaseHub** — structural distinction between DECLINED, FAILED, WITHDRAWN, DELEGATED. GUPP is a design principle, not a tracked lifecycle |
| Trust from outcomes | Wasteland stamps: multi-dimensional, evidence-anchored, but human-curated | FULFILLED → LedgerAttestation → TrustGatedAttestationPolicy → TrustScoreJob → Bayesian Beta update | **CaseHub** — the closed feedback loop is live; trust updates from every agent interaction without human curation |
| DECLINED vs FAILED | Indistinguishable (bead closes with result) | Structurally distinct commitments | **CaseHub** — "I can't do this" vs "I tried and failed" are different operational responses |
| Delegation chain | Session crash → next session picks up from hook (GUPP) | HANDOFF with full `causedByEntryId` chain | **CaseHub** — six months later the chain is readable, not reconstructed. Gas City's GUPP is operationally effective but the delegation history is implicit in bead ordering |
| Stalled detection | Patrol-based self-healing (poll + exponential backoff) | `list_stalled_obligations` + WatchdogEvaluationService | **Gas City** — patrols re-check world state from scratch each iteration, making them self-healing. CaseHub detects but does not yet auto-recover |
| Ledger integration | All messages recorded in Dolt (SQL-queryable, git-versioned) | `MessageLedgerEntry extends LedgerEntry` — all 9 speech-act types in tamper-evident Merkle chain | **CaseHub** — Merkle chain is independently verifiable; Dolt is admin-trusted |

### 4.3 Trust and Reputation

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Model | Multi-dimensional stamps (quality/reliability/creativity axes) with confidence level and severity weighting | Bayesian Beta (auto-computed from attestations) + EigenTrust (collusion-resistant) | **CaseHub** — mathematically grounded, auto-computed. Gas City's stamps are richer than Gas Town's but still human-curated |
| Evidence anchoring | Each stamp links to specific completion artifacts (commits, links, descriptions) | FULFILLED → LedgerAttestation with confidence modulated by trust tier | **Both strong, different model** — Gas City links to artifacts; CaseHub's attestation confidence is computed from agent trust tier |
| Capability-scoped scoring | Per-axis stamps (quality, reliability, creativity) | `ScoreType.CAPABILITY` with per-capability thresholds + `ScoreType.CAPABILITY_DIMENSION` quality floors | **CaseHub** — more granular; an agent's security-review quality tracked separately from style-review |
| Routing integration | Stamps don't drive routing — informational for reputation | `TrustWeightedAgentStrategy @Priority(1)` → `SemanticAgentRoutingStrategy @Priority(2)` → `LlmAgentRoutingStrategy @Priority(100)` → `CbrAgentRoutingStrategy @Priority(101)` | **CaseHub** — 4-layer routing architecture driven by trust scores. Gas City's stamps are reputation, not routing signals |
| Anti-fraud | "Collusion rings have a distinctive topology — lots of mutual stamping, sharp boundaries, no outside critics." Stamp graph traversable and auditable | EigenTrust (Kamvar 2003) — provably collusion-resistant transitive trust | **CaseHub** — mathematically provable. Gas City relies on topology detection, which is pattern-based |
| Cross-deployment | Wasteland: federated via DoltHub, portable identity, connecting thousands of instances | TrustExportService + TrustImportService (shipped, ledger#63-65) | **Gas City** — production federation at scale. CaseHub is shipped but not deployed at scale |
| Cold-start handling | Three-tier trust ladder (Registered → Contributor → Maintainer) | Four-phase maturity model; Phase 0 = Gas City parity | **CaseHub** — never blocks on missing trust data; improves automatically |
| Temporal decay | Not documented | Exponential decay; FLAGGED persistence multiplier | **CaseHub** — recent evidence counts more; negative signals persist longer |
| Behavioral signals | Not present | `BehavioralSignalStore` SPI — DECLINE count, SUCCESS, COMPLIANT, VIOLATED. Per-signal TTL. DECLINE threshold → Excluded(LEARNED) | **CaseHub** — agents learn from their own behavioral patterns |

### 4.4 Audit and Compliance

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Tamper evidence | Dolt git-for-SQL (admin-trusted, SQL-queryable) | Merkle MMR (cryptographic, independently verifiable) | **CaseHub** — admin cannot rewrite Merkle history; inclusion proofs verifiable without server access |
| Ed25519 bilateral signing | No | Platform signs COMMAND, agent signs RESPONSE | **CaseHub** — neither side can repudiate |
| Inclusion proofs | No | Publishable to external transparency log | **CaseHub** — third-party verification without server access |
| GDPR Art.17 erasure | No | LedgerErasureService + tamper-evident receipt + cross-tenant entity wipe | **CaseHub** — prove data was erased, prove when, prove chain integrity preserved |
| GDPR Art.22 decision records | No | ComplianceSupplement (EU AI Act Art.12 fields) | **CaseHub** — purpose-built regulatory compliance |
| W3C PROV-DM lineage | No | LedgerProvExportService + `causedByEntryId` chain | **CaseHub** — explicit causal chain, not reconstructed |
| Multi-framework compliance | SOC2 narrative via Dolt audit trail | 6 frameworks (SOC2, GDPR, EU AI Act, DORA, NIS2, ISO27001) via casehub-ops. Evidence-based drift detection. Five-category posture scoring | **CaseHub** — continuous compliance management vs point-in-time audit narrative |
| Secret management | Environment stripping + pattern-based redaction (TOKEN, PASSWORD, SECRET, etc.) | Vault-backed KMS (AppRole, K8s, JWT auth). Cloud KMS adapters (AWS, GCP, Azure, Vault Transit) | **CaseHub** — production-grade secret management vs environment sanitization |
| Authority model | 4-tier: trusted operator, trusted dependency, untrusted data, ambient environment. 14 execution surfaces documented | ACL (resource-level, group-based grants, parent inheritance) + RBAC (`@RolesAllowed` via OIDC) + tenant isolation | **Both strong, different model** — Gas City documents trust boundaries per execution surface; CaseHub enforces access control per resource and role |

### 4.5 Human-in-the-Loop

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Human task model | Bead with interactive session (attach/peek) | WorkItem — 10-status lifecycle with SLA, delegation, escalation, spawn | **CaseHub** — dedicated model for human work with distinct semantics from agent work |
| SLA enforcement | None | expiresAt + ClaimDeadlineJob + ExpiryCleanupJob + SlaBreachPolicy SPI (Fail/EscalateTo/Extend with fallback chaining) | **CaseHub** — time-bounded human obligations with automatic escalation |
| Gate mechanism | Formula-based convergence loops with shell check scripts | WorkItem DELEGATED + EscalationPolicy; ActionRiskClassifier SPI — 8 types, 4 categories | **CaseHub** — platform-enforced accountability vs user-configured guardrails |
| Philosophy | "Light Factory" — all agents visible, guardrails user-configured, "reliability is a dial" | Platform-enforced accountability — consequential actions gated via WorkItem before case advances | **Design choice** — Gas City optimizes for operator control; CaseHub optimizes for compliance-grade accountability |
| Session inspection | `gc session attach` (interactive) / `gc session peek` (snapshot) | WorkItem dashboard + MCP tools (`get_queue_status`, `list_problems`, `get_reviewer_health`) | **Parity** — different UIs, both provide visibility. CaseHub adds AI-operable inspection via MCP |

### 4.6 Agent Identity and Routing

New section — Gas Town had nothing here; Gas City has minimal; CaseHub has a full subsystem.

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Agent identity model | Role name in pack config (e.g. "reviewer", "planner") | 4-layer `AgentDescriptor`: identity, slot (Belbin/DISC vocabulary), capabilities (with routing signals), disposition (multi-axis personality) | **CaseHub** — structured identity that drives routing, probing, and prompt generation |
| Capability declaration | Implicit from pack assignment | `AgentCapability` with qualityHint, latencyHintP50Ms, costHint, epistemicDomains, inputTypes, outputTypes | **CaseHub** — capabilities are typed routing signals, not implicit from role |
| Health probing | Patrol heartbeat (agent alive?) | `CapabilityHealth.probe()` → Ready, Degraded, Unavailable, EpistemicallyWeak. Checks `AgentStateStore` (rate limited? service down? quota exceeded?) then epistemic domain confidence | **CaseHub** — per-capability health at dispatch time, not just alive/dead |
| Routing | Prefix-based (`routes.jsonl`), metadata-based (`gc.routed_to`), formula step `run_target` | 4-layer: trust-weighted → semantic → LLM-reasoned → CBR-evidence. Content-driven binding conditions on code analysis results | **CaseHub** — routing decisions use trust history, semantic similarity, past case outcomes, and code content analysis. Gas City routes by structural metadata |
| Behavioral learning | Not present | `BehavioralSignalStore`: DECLINE count ≥ threshold → Excluded(LEARNED). VIOLATED per-dimension → BehavioralViolation. Per-signal TTL | **CaseHub** — agents that repeatedly decline a capability type are excluded from routing to it |
| System prompt generation | Pack-defined prompt templates with variable substitution | `SystemPromptRenderer` → MARKDOWN, PROSE, or A2A_CARD format. Two-step: structural assembly → optional ChatModel semantic pass | **CaseHub** — prompts generated from structured identity, not template strings |
| Cross-vocabulary equivalence | Not present | `VocabularyRegistry.equivalentValues()` — e.g. a DISC "D" type maps to Thomas-Kilmann "Competing" on CONFLICT_MODE axis | **CaseHub** — agents described in different vocabularies can be compared for routing |

### 4.7 Agent Oversight and Recovery

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Detection | Patrol-based (re-checks world state from scratch each iteration) | WatchdogEvaluationService + `list_stalled_obligations` | **Gas City** — patrols are self-healing by design; CaseHub detects but does not yet auto-recover |
| Recovery | NDI: "keep throwing agents at it." Crash loop protection: max_restarts + restart_window → quarantine. Session circuit breaker with auto-reset cooldown | Detection alerts only — RecoveryPolicy SPI designed but not implemented | **Gas City** — CaseHub's most significant gap. NDI is pragmatically powerful |
| Concurrency control | Pool-based scaling: scale_check query per tick, max/min_active_sessions, tick debounce, probe_concurrency cap, max_wakes_per_tick | Not built | **Gas City** — production-proven at hundreds of agents |
| Runtime providers | 7 (tmux, subprocess, exec, ACP, K8s, hybrid, herdr) | Claude CLI (ClaudeAgentProvider) + LangChain4j bridge + OpenClaw | **Gas City** — broader runtime support. CaseHub's LangChain4j bridge provides model-agnostic access but fewer session backends |

### 4.8 Extensibility

| Dimension | Gas City | CaseHub | Notes |
|-----------|---------|---------|-------|
| Extension model | Pack import + Factory Worker API + runtime provider abstraction | SPI-based (compile-time verified) + CDI priority resolution + optional module activation via classpath | **CaseHub** — misconfigured SPI fails at build time. Gas City's pack composition is more operator-friendly |
| Ecosystem | 9 CLI agents, 7 runtime providers, community packs (gascity-packs repo) | Full Quarkiverse (Kafka, Redis, gRPC, Elasticsearch, etc.) + 6 worker dispatch types (HTTP, Camel, GitHub Actions, MCP, K8s, Script) | **Both broad, different axis** — Gas City: agent runtime breadth. CaseHub: infrastructure integration breadth |
| Multi-domain | Architecturally supported (MEOW is domain-agnostic). Shipped packs all coding-focused | 8 application repos proven (devtown, aml, clinical, life, drafthouse, quarkmind, soc, fsitrading) | **CaseHub** — proven at scale, not just architecturally supported |
| Composability | Packs: directory with pack.toml, import via Git URL, hierarchical override resolution | CasePlanModel YAML (per-case declarative) + blocks pattern library (reusable orchestration primitives) | **Different level** — packs compose what-to-do; blocks compose how-to-coordinate |

---

## 5. Application vs Application

### 5.1 What Gas City Provides

Production at v1.0. Proven at hundreds of concurrent agents.

| Capability | Mechanism |
|-----------|-----------|
| Merge queue | Refinery — Bors batch-then-bisect (recursive binary isolation) |
| AI coding agents | Polecats in git worktrees with persistent bead identity |
| Human workspaces | Interactive session attach/peek, "Light Factory" |
| Cross-rig routing | `routes.jsonl` transparent bead routing + work query 3-tier priority |
| Workflow templates | Formulas v2 (TOML) with drain fan-out, convergence, multi-lane review |
| CLI | `gc` — comprehensive session, rig, and bead management |
| Predecessor context | Bead history + GUPP hook persistence |
| Federated reputation | Wasteland via DoltHub — connecting thousands of instances |
| Pack ecosystem | gascity-packs: build-basic, bmad-build, compound-build, superpowers-build, gstack-build, slack integrations, pr-pipeline, contributing |
| Durable work | MEOW stack: Bead→Epic→Molecule→Protomolecule→Formula |
| NDI recovery | Nondeterministic idempotence — sessions crash-resume against acceptance criteria |
| Pool scaling | Per-agent scale_check, max/min_active_sessions, crash loop quarantine |
| Multi-agent model | Claude, Codex, Gemini, Amp, OpenCode, Pi, Grok Build, Groq, Cerebras |
| Runtime providers | 7: tmux, subprocess, exec, ACP, K8s, hybrid, herdr |

### 5.2 What casehub-devtown Provides

Pre-production. Six layers + merge queue + governance + trust pipeline + CBR active.

Unchanged entries from v5 retained. New entries:

| Capability | Foundation primitive | Status |
|-----------|---------------------|--------|
| Content-driven PR routing | CasePlanModel — bindings gate on code content | Shipped (Layer 5) |
| Human review gate with SLA | WorkItem + SlaBreachPolicy SPI | Shipped (Layer 2) |
| Typed agent messaging | Qhorus COMMAND/RESPONSE/DONE/DECLINE per reviewer | Shipped (Layer 3) |
| Tamper-evident merge audit | MergeDecisionLedgerEntry + ComplianceSupplement | Shipped (Layer 4) |
| Trust-weighted reviewer routing | TrustWeightedAgentStrategy + DevtownTrustRoutingPolicyProvider | Shipped (Layer 6) |
| Trust-gated attestation | TrustGatedAttestationPolicy — confidence modulation by trust tier | Shipped (devtown#97) |
| Merge queue | CasePlanModel batch-then-bisect + 3 bisection strategies + adaptive sizing | Shipped (devtown#11) |
| CBR-enhanced routing | CbrAgentRoutingStrategy with typed FeatureValue similarity | Active in devtown |
| Agent identity integration | casehub-eidos descriptors + CapabilityHealth probing at dispatch | Available via engine integration |
| Orchestration patterns | casehub-blocks: supervisor, sequence, loop, parallel, voting, debate, conditional, HTN | Available |
| 4-layer routing | Trust → semantic → LLM → CBR routing chain | Available via engine + blocks |
| OpenClaw accountability | CaseHub MCP tools for OpenClaw agents (casehub_commit, casehub_done, etc.) | Available via openclaw |
| GDPR Art.17 erasure | LedgerErasureService with tamper-evident receipt | Shipped (devtown#74) |
| Action oversight gates | DevtownActionRiskClassifier — 8 types, 4 categories | Shipped (devtown#56) |
| 16 MCP tools | 11 read + 5 write for AI-as-operator | Shipped (devtown#17) |
| PR governance workbench | Dashboard for case lifecycle, supersede/relink | Shipped (devtown#85) |

### 5.3 Gas City Feature Parity Checklist

| Gas City feature | devtown approach | Status |
|----------------|-----------------|--------|
| Merge queue (Bors batch-then-bisect) | CasePlanModel + 3 bisection strategies + adaptive batch sizing | Shipped |
| AI coding agents (Polecats) | Claudony WorkerProvisioner | Foundation ready |
| Human workspaces (interactive) | WorkItem via casehub-work + governance workbench | Shipped |
| Cross-rig routing | Sub-case orchestration | Foundation ready |
| CLI tooling (`gc`) | 16 MCP tools — AI-native, protocol-native | Shipped |
| Predecessor context | `get_prior_decisions` MCP tool + CaseMemoryStore | Partial |
| Federated reputation (Wasteland) | TrustExportService + TrustImportService | Shipped, not deployed at scale |
| Pack ecosystem | CasePlanModel YAML + blocks patterns | Partial — no sharing mechanism |
| MEOW durable work | CasePlanModel + engine blackboard + PlanItemStore persistence | Shipped |
| NDI recovery | Engine re-evaluates bindings — architecturally equivalent but not operationally proven | Foundation ready, needs P1.2 |
| Pool scaling | SpawnThrottle in ClaudonyConfig | Not started |
| Crash loop protection | Not built | Not started |
| 9 CLI agents | Claude (ClaudeAgentProvider) + LangChain4j bridge | Partial |
| 7 runtime providers | Claude CLI + OpenClaw | Partial |
| Factory Worker API | WorkerProvisioner SPI + WorkerFunction interface | Equivalent SPI, fewer implementations |
| ACP protocol | MCP (AI-native) + Qhorus (normative) | Different protocols — CaseHub uses standards (MCP/CloudEvents); Gas City uses proprietary ACP |
| Multi-lane review | CasePlanModel bindings fire N checks simultaneously | Shipped |
| Convergence loops | Not built as a pattern | blocks pattern library has `loop` pattern |

---

## 6. Where CaseHub Surpasses Gas City

Capabilities that require a structural rewrite in Gas City to match.

### 6.1 The Closed Feedback Loop

Unchanged from v5 — Gas City has no mechanism to close this loop. Wasteland stamps are richer than Gas Town's but still human-curated and do not feed back into routing automatically.

### 6.2 4-Layer Routing Architecture

New in v6. Gas City routes by prefix, metadata, and formula step target. CaseHub routes by trust score, semantic similarity, LLM reasoning, and CBR evidence from analogous past cases. The routing decision is informed by the agent's trust history (Layer 1), policy configuration (Layer 2), capability match (Layer 3), and past case similarity (Layer 4). Gas City would need to build trust scoring, semantic embedding, LLM integration, and case-based reasoning infrastructure to match this.

### 6.3 Agent Identity (casehub-eidos)

New in v6. Gas City identifies agents by role name in pack configuration. CaseHub provides 4-layer structured identity (identity/slot/capabilities/disposition), capability health probing at dispatch time (Ready/Degraded/Unavailable/EpistemicallyWeak), behavioral signal accumulation (learned routing exclusion), vocabulary-based disposition profiling (Belbin, DISC, Thomas-Kilmann), and system prompt generation in three formats (MARKDOWN/PROSE/A2A_CARD). This is an entire subsystem Gas City has no equivalent for.

### 6.4 Case-Based Reasoning

New in v6. CaseHub's CBR pipeline learns from analogous past cases: typed feature extraction (FeatureValue sealed hierarchy with DTW for temporal features), hybrid retrieval (semantic + metadata), plan adaptation (PlanAdapter SPI), outcome feedback (RoutingOutcomeRecorder), and temporal decay with supersession. Active in 6+ application repos. Gas City has no learning-from-similar-past-cases capability.

### 6.5 Composable Orchestration Patterns

New in v6. casehub-blocks provides sealed types for routing, decomposition, activation, aggregation, and termination decisions, with pattern builders (supervisor, sequence, loop, parallel, voting, debate, conditional, HTN) and a fluent DSL. ExecutionPlan DAG type with dependency edges. Hybrid decomposition (static-first, LLM fallback). Gas City's formulas v2 add control flow but are template-based, not pattern-based. CaseHub's blocks compose coordination primitives; Gas City's packs compose workflow steps.

### 6.6 Action Oversight Gates

Unchanged from v5 — Gas City's "Light Factory" philosophy makes guardrails user-configured, not platform-enforced. CaseHub gates consequential actions through WorkItem before the case advances.

### 6.7 GDPR-Compliant Tamper-Evident Erasure

Unchanged from v5 — Gas City has no GDPR compliance story.

### 6.8 Multi-Framework Continuous Compliance

New in v6. casehub-ops manages compliance posture across 6 frameworks (SOC2, GDPR, EU AI Act, DORA, NIS2, ISO27001) with evidence-based drift detection and five-category posture scoring. Gas City's Dolt audit trail provides a SOC2 narrative, but that is one framework and retrospective rather than continuous.

### 6.9 Proven Multi-Domain Layering

Updated from v5. Eight application repos (devtown, aml, clinical, life, drafthouse, quarkmind, soc, fsitrading) share the same foundation. Gas City's MEOW stack is domain-agnostic architecturally, but all shipped packs in gascity-packs are software-engineering focused. CaseHub has proven the multi-domain claim across regulated domains (AML, clinical), consumer domains (life), and entertainment (quarkmind).

### 6.10 AI-Native Operational Tooling

Unchanged from v5 — Gas City's `gc` CLI is shell-only. CaseHub's 16 MCP tools provide AI-as-operator capability.

### 6.11 Merge Queue — Ten Architectural Differences

Unchanged from v5. Gas City's Refinery is still Bors-style with positional bisection.

### 6.12 Trust Pipeline — Self-Reinforcing Confidence

Unchanged from v5 — Gas City has no trust pipeline that feeds back into routing.

### 6.13 Content-Driven Routing

Updated from v5. Gas City routes by structural metadata (prefixes, explicit run_target annotations, agent role names). CaseHub routes by binding conditions that evaluate code content analysis results — security review fires because code analysis found cryptographic code, not because a human labelled the PR.

### 6.14 On-Device Neural Inference

New in v6. casehub-neocortex provides ONNX neural text inference (NLI, classification, regression, reranking), SPLADE sparse embeddings, and CRAG (corrective RAG). Gas City has no on-device inference capability.

---

## 7. Where Gas City Still Leads

Not minimised. These are real operational advantages, and Gas City has widened several of them compared to Gas Town.

### 7.1 Operational Maturity

Gas City is v1.0, in production at hundreds of concurrent agents. Julian Knutsen has run "hundreds of concurrent workers in a city." Chris Sells' team achieved 74 PRs in a single day. CaseHub is pre-production. This remains the most significant single fact.

### 7.2 Patrol-Based Self-Healing and NDI

Expanded from v5. Gas City's patrol model re-checks world state from scratch each iteration with exponential backoff, making agents self-healing. NDI (nondeterministic idempotence) explicitly embraces LLM non-determinism: "since LLM steps are nondeterministic, durability is achieved because agent, hook, and molecule are all persistent beads in Git. Acceptance criteria are well-specified per step. As long as you keep throwing agents at it, the outcome converges."

CaseHub's architectural equivalent — engine binding re-evaluation on context change — is theoretically sound but the RecoveryPolicy SPI is not implemented. CaseHub cannot throw agents at failed steps today.

### 7.3 Concurrency Control and Crash Loop Protection

Expanded from v5. Gas City's pool-based scaling: scale_check query per tick, max/min_active_sessions, max_wakes_per_tick (default 5), probe_concurrency (default 8), tick_debounce. Crash loop protection: max_restarts (default 5) within restart_window (default 1h) → quarantine. Session circuit breaker with auto-reset cooldown. assigned_work_defer_limit prevents unbounded wake/idle-kill treadmills.

CaseHub's SpawnThrottle is designed but not started. At 10+ concurrent cases this is a hard failure.

### 7.4 Agent Ecosystem Breadth

New in v6. Gas City supports 9 CLI agents (Claude, Codex, Gemini, Amp, OpenCode, Pi, Grok Build, Groq, Cerebras) and 7 runtime providers (tmux, subprocess, exec, ACP, K8s, hybrid, herdr). CaseHub primarily supports Claude (ClaudeAgentProvider + ClaudeAgentSession) with LangChain4j bridge for model-agnostic access and OpenClaw for non-Claude agents. CaseHub's approach is more architecturally sound (SPI-based, ChatModel abstraction) but has fewer production implementations.

### 7.5 Pack Ecosystem

New in v6. Gas City's gascity-packs repository contains shareable workflow templates: build-basic, bmad-build, compound-build, superpowers-build, gstack-build, slack integrations, pr-pipeline, contributing. Packs compose via Git URL import with hierarchical override resolution. CaseHub has CasePlanModel YAML definitions per application and blocks orchestration patterns, but no sharing/discovery/import mechanism equivalent to packs.

### 7.6 Federated Reputation at Scale

New in v6. The Wasteland connects thousands of Gas City instances via DoltHub with portable identity. Multi-dimensional stamps with anti-fraud topology detection. CaseHub's TrustExportService + TrustImportService are shipped but not deployed at scale.

### 7.7 The Pattern — Updated

**Every area where Gas City leads is operational** — things needed to run at scale. **Every area where CaseHub leads is structural** — things that cannot be bolted on after the fact.

This pattern holds from v5, but Gas City's operational advantages are now deeper and more architecturally sophisticated than Gas Town's were. Patrol-based NDI is not just an operational feature — it is a design philosophy for LLM unreliability that CaseHub should learn from. Pool-based scaling with crash loop protection is production engineering that CaseHub needs before deployment.

However: Gas City's structural ceiling is unchanged. Packs decompose hardcoded roles into composable configuration — a real advance. But the coordination paradigm is still workflow. The trust system is still human-curated. The compliance story is still admin-trusted Dolt. The normative layer gives lifecycle tracking, not formal accountability. The routing is prefix-based, not content/trust/CBR-driven. These are foundation-level limitations that packs cannot address.

---

## 8. Composability: Packs vs Blocks vs CasePlanModel

This section is new in v6 — the composability comparison deserves explicit treatment because both sides have invested heavily here.

### Three composition models

| | Gas City Packs | CaseHub CasePlanModel | CaseHub Blocks |
|---|---|---|---|
| **What it composes** | Workflow templates (what agents do) | Case definitions (what goals to achieve) | Orchestration patterns (how to coordinate) |
| **Unit** | Directory with `pack.toml` | YAML file with bindings, goals, milestones | Sealed Java types with pattern builders |
| **Import** | Git URL or local path in `city.toml` | Maven dependency (classpath) | Maven dependency (classpath) |
| **Override** | Hierarchical: city > rig > import > agent-local | CDI `@Priority` displacement | CDI `@Priority` displacement |
| **Sharing** | Git repo (gascity-packs) | No sharing mechanism yet | Maven artifact |
| **User** | Operators (configure what agents do) | Domain developers (declare case goals) | Platform developers (compose coordination) |
| **Abstraction level** | Process template | Goal declaration | Coordination primitive |

### Analysis

Gas City's packs are immediately useful — an operator can import `build-basic` and have a working development workflow in minutes. This is a significant onboarding advantage.

CaseHub's model is more general — CasePlanModel YAML declares goals and binding conditions; blocks provide composable coordination primitives (supervisor, sequence, loop, parallel, voting, debate, conditional, HTN). But this generality comes at a cost: there is no equivalent of "import a pack and go." Building a CaseHub application requires implementing domain logic, configuring SPIs, and wiring bindings.

**The gap:** CaseHub needs a template/pack-equivalent mechanism for sharing CasePlanModel definitions + blocks configurations across deployments. This would give operators the Gas City onboarding experience while preserving CaseHub's structural advantages.

---

## 9. Remaining Roadmap

### 9.1 Foundation Gaps (competitive response)

| Gap | What it is | Priority | Benchmark |
|-----|-----------|----------|-----------|
| P1.1 Concurrency throttle | `SpawnThrottle` in ClaudonyConfig — cap concurrent worker sessions | High | Gas City: pool-based scaling, max/min_active_sessions, crash loop quarantine |
| P1.2 Recovery automation | `RecoveryPolicy` SPI — REPROVISION, ESCALATE_TO_HUMAN, CANCEL_CASE | High | Gas City: patrol-based self-healing + NDI |
| Agent ecosystem breadth | Additional `AgentProvider` implementations + runtime provider abstraction | Medium | Gas City: 9 CLI agents + 7 runtime providers |
| CasePlanModel template ecosystem | Shareable template mechanism (import, compose, override) | Medium | Gas City: packs with Git URL import and hierarchical override |
| P1.5 Doltgres backend | Configurable `casehub.ledger.backend=doltgres` | Low | Nice-to-have |

### 9.2 Application Epics

Unchanged from v5. #12 (cross-repo merge), #13 (trust routing full), #14 (failure handling), #15 (GitHub integration), #16 (notification wiring), #129 (CBR).

### 9.3 Where the Roadmap Leaves Us

**After P1.1 + P1.2:** CaseHub can run at agent scale. Gas City's two core operational advantages close. The patrol/NDI philosophy should inform the implementation — CaseHub's RecoveryPolicy should support "retry with new agent" as a first-class recovery action.

**After agent ecosystem breadth:** CaseHub supports the same agents Gas City does, via SPI-based providers rather than pack configuration. The LangChain4j bridge already provides model-agnostic access; the gap is in session management providers (only tmux/Claude CLI today).

**After CasePlanModel template ecosystem:** Operators can import and compose case definitions the way Gas City operators import packs. This closes the onboarding gap without sacrificing CaseHub's structural advantages.

At that point, CaseHub matches Gas City's operational capabilities while providing structural advantages Gas City cannot match: closed feedback loop, 4-layer routing, agent identity, CBR, content-driven routing, cryptographic compliance, formal accountability, and composable orchestration patterns.

---

## 10. Technology Stack

| Dimension | Gas City | CaseHub |
|-----------|---------|---------|
| Language | Go | Java 21 (on Java 26 JVM) |
| Persistence | Dolt SQL Server (git-versioned) | PostgreSQL (default, Flyway) / MongoDB (casehub-work) / Qdrant (vector store) |
| Runtime | Go binary | GraalVM native image (0.084s) or JVM |
| Coordination | Patrol loop (desired-state reconciliation) + formula step execution | Vert.x event loop + Mutiny reactive + CMMN blackboard |
| Workflow | Formulas v2 (TOML) + MEOW stack | CasePlanModel (YAML) + blocks pattern library (Java sealed types) |
| Composition | Packs (directory + pack.toml, Git URL import) | SPI + CDI + CasePlanModel YAML |
| Message protocol | ACP (JSON-RPC over stdio) + mail system | Qhorus (9 speech-act types, MCP tools, A2A compatible) |
| Observability | Dolt audit trail (SQL-queryable, git-versioned) | OTel + Merkle tamper evidence + W3C PROV-DM |
| Compliance | SOC2 narrative via Dolt | GDPR Art.17/22, EU AI Act Art.12, SOC2, DORA, NIS2, ISO27001 via casehub-ops |
| Agent support | 9 CLI agents (Claude, Codex, Gemini, Amp, OpenCode, Pi, Grok Build, Groq, Cerebras) | Claude (primary) + LangChain4j bridge + OpenClaw |
| Runtime providers | 7 (tmux, subprocess, exec, ACP, K8s, hybrid, herdr) | Claude CLI + OpenClaw |
| Interface | `gc` CLI | 16+ MCP tools + REST APIs + governance workbench |
| Extension model | Pack import + Factory Worker API | SPI-based (compile-time verified) + CDI priority |
| Ecosystem | Go stdlib + community packs | Full Quarkiverse + 6 worker dispatch types |
| Foundation reuse | MEOW is domain-agnostic; shipped packs are coding-only | 8 application repos across domains |
| Trust | Wasteland multi-dimensional stamps (federated) | Bayesian Beta + EigenTrust (auto-computed, routing-integrated) |
| AI/ML | None on-device | ONNX inference, SPLADE, CRAG, configurable RAG fusion |
| Agent identity | Role name in pack config | casehub-eidos: 4-layer descriptors, vocabulary, health probing |
| Version | v1.0 (production, hundreds of agents) | Pre-production (6 layers + merge queue + CBR active) |
