# CaseHub vs Gastown: Comprehensive Analysis

> **Status:** Working document — not committed. Do not push.
> **Date:** 2026-04-27
> **Purpose:** Gap analysis, differentiation, and strategic positioning between the CaseHub ecosystem and Gastown.

---

## 1. Executive Summary

These are not competing implementations of the same idea. They answer different questions and operate at different layers.

**Gastown** asks: *how do I coordinate many AI agents across software engineering tasks at scale?* It is a domain-specific application — a sophisticated system for AI coding agent coordination with agent lifecycle management, a CLI, a daemon, a merge queue, and git/CI integration baked in. It is in production at v1.0.1.

**CaseHub** asks: *how do I build a foundation that enables formally accountable, self-improving, adaptive AI coordination deployable across any domain?* It is a platform — a domain-agnostic foundation layer of composable primitives (case engine, normative layer, trust model, human task lifecycle, audit ledger) on top of which domain-specific applications are built. It is pre-production.

**The most fundamental architectural difference is layering:**

```
┌───────────────────────────────────────────────────────────────┐
│                      Application Layer                         │
│  casehub-refinery   │  casehub-healthcare  │  casehub-legal   │
│  (AI coding agents) │  (clinical workflows)│  (case mgmt)     │
├───────────────────────────────────────────────────────────────┤
│                      Foundation Layer                          │
│  casehub-engine  │  quarkus-qhorus    │  quarkus-ledger       │
│  casehub-work    │  casehub-connectors│  (+ Quarkus ecosystem)│
└───────────────────────────────────────────────────────────────┘
```

Gastown is one of those application-layer boxes — the AI coding agent coordination app — built without a separable foundation underneath. Its git integration, merge queue, rig/worktree model, and agent lifecycle management are inseparable from the infrastructure. You cannot reuse Gastown's coordination layer for healthcare case management or financial compliance. The domain is baked into the foundation.

CaseHub's foundation has no domain knowledge. The merge queue is not in `casehub-engine` — it is a future application (`casehub-refinery`) that uses CaseHub's primitives. The foundation doesn't need to know anything about git, PRs, or CI. That is the application's concern.

This layering is CaseHub's strategic moat: one foundation, many application domains. Gastown can only ever be one application.

The second fundamental difference: Gastown drives agents through predefined steps. CaseHub declares goals and lets the system discover the path — the Adaptive Case Management paradigm. For AI agent coordination where agent outputs determine what work comes next, ACM is the correct paradigm and workflow is the wrong one.

---

## 2. System Overviews

### 2.1 CaseHub Ecosystem

| Repo | GitHub | Purpose | Tier |
|------|--------|---------|------|
| `casehub-parent` | casehubio/parent | BOM, CI dashboards, full-stack build | — |
| `quarkus-ledger` | casehubio/ledger | Immutable tamper-evident audit ledger, Bayesian trust scoring, GDPR compliance | Foundation |
| `casehub-work` | casehubio/casehub-work | Human task lifecycle (WorkItem inbox, SLA, delegation, escalation) | Foundation |
| `quarkus-qhorus` | casehubio/qhorus | Agent communication mesh (speech acts, commitments, typed channels) | Foundation |
| `casehub-connectors` | casehubio/connectors | Outbound message connectors (Slack, Teams, SMS, email) | Foundation |
| `casehub-engine` | casehubio/engine | Hybrid choreography+blackboard ACM engine | Orchestration |
| `claudony` | casehubio/claudony | Remote Claude CLI sessions, CaseHub SPI implementations, dashboard | Integration |

**External integrations (Quarkus ecosystem):**
- `quarkus-flow` — CNCF Serverless Workflow engine (worker type in casehub)
- `drools` — Business rules engine (pluggable binding evaluator, via open SPI)
- Full Quarkiverse — Kafka, Redis, MongoDB, gRPC, OIDC, Micrometer, Elasticsearch, etc.

### 2.2 Gastown

| Component | Purpose |
|-----------|---------|
| `Dolt SQL Server` | Single persistent store per town (git semantics for SQL) |
| `gt` CLI | All agent and operator interactions |
| `Mayor` | Global coordinator, convoy management, cross-rig communication |
| `Deacon` | Cross-rig daemon watchdog with patrol cycles |
| `Boot / Dog` | Validates Deacon every 5 min; infrastructure workers |
| `Witness` | Per-rig agent monitoring polecats and refinery |
| `Refinery` | Merge queue (Bors-style batch-then-bisect) |
| `Polecat` | Ephemeral AI agent worker with persistent bead identity |
| `Crew` | Long-lived human workspace with full git clone |
| `beads` | CLI for atomic work record management |
| `Wasteland` | Federated identity and reputation via DoltHub |
| `gt-proxy-server` | Sandboxed container execution with mTLS |

**Key abstractions:**

| Abstraction | Definition |
|-------------|-----------|
| Bead | Atomic work unit (Dolt SQL, 6-stage lifecycle: CREATE→LIVE→CLOSE→DECAY→COMPACT→FLATTEN) |
| Convoy | Bundle of related beads with unified visibility |
| Formula | TOML-based workflow template (reusable operational patterns) |
| Molecule | Durable chained bead workflows (survive agent restarts) |
| Hook | Pinned bead serving as agent work queue |
| Wisp | Ephemeral bead, destroyed after use |
| GUPP | "If there is work on your Hook, YOU MUST RUN IT" |

---

## 3. Fundamental Paradigm Difference

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| **Paradigm** | Workflow / task coordination | Adaptive Case Management (ACM) |
| **Core question** | What steps must be executed? | What needs to be achieved? |
| **Structure** | Fixed at design time (formulas, convoys) | Emerges at runtime from goal evaluation |
| **Path to completion** | Enumerated in formula | Discovered by binding evaluation over blackboard state |
| **Parallelism** | Explicitly declared in formula structure | Automatically exploited when multiple bindings satisfied |
| **Failure handling** | Agent-side logic or Witness recovery | Engine re-evaluates bindings with updated context; alternative paths fire |
| **Termination** | All steps executed | Goals satisfied |
| **Unexpected work** | Agent must re-sling or escalate | New knowledge on blackboard triggers new bindings |
| **Theoretical basis** | Operational evolution from practice | Hayes-Roth Blackboard Architecture + CMMN standard |

**Why ACM is correct for AI agent coordination:** AI agents produce information. The output of a code review agent (findings, severity, recommendations) changes what work is needed next in ways that cannot be fully anticipated at design time. ACM handles this natively. Workflow requires you to pre-enumerate all possible paths — which is impossible when agent outputs are the inputs to routing decisions.

---

## 4. CaseHub Architecture — Deep Detail

### 4.1 Binding System (casehub-engine)

The choreography engine evaluates bindings on every `CaseContextChangedEvent`. Bindings are the core coordination primitive.

| Trigger Type | Mechanism | Example |
|-------------|-----------|---------|
| `contextChange` | JQ predicate over `CaseContext` JSON | `.reviews | map(select(.verdict=="APPROVED")) | length >= 2` |
| `cloudEvent` | CNCF CloudEvent type/source/subject match + optional JQ | Any external system speaking CloudEvents standard |
| `schedule` | Cron or ISO-8601 duration | `"0 9 * * 1-5"` (weekdays at 9am) |

**Binding conditions are pluggable:**
- JQ expressions (current default)
- Java lambdas (`ctx -> ctx.get("document") != null`)
- Drools DRL / DMN (via pluggable evaluator SPI — architecture supports it)
- Any custom evaluator via SPI

**This means a single binding can express:** "start this worker when condition X over accumulated case knowledge is true AND a CloudEvent from billing confirms payment AND it's a weekday." No agent reasons about this. The engine evaluates it on every state change.

### 4.2 Worker Execution Models

Three first-class execution models, all peers in the binding system:

| Model | Class | When to use |
|-------|-------|-------------|
| **Lambda** | `Worker.lambda("name", capabilities, ctx -> ...)` | Deterministic, in-process, no external call |
| **Workflow** | `Worker.workflow("name", capabilities, workflowDef)` | Structured multi-step (Quarkus Flow, CNCF Serverless Workflow) |
| **Agent** | `Worker.agent("name", capabilities)` | AI agent with autonomous reasoning (Claude, Gemini, etc.) |
| **Hybrid** | Workflow with agent steps | Step 1: lambda setup. Step 2: agent reasoning. Step 3: lambda validation |

A case plan can mix all four in any combination, all routed by the same `WorkBroker`, all recorded in the same ledger.

### 4.3 Worker Registration Modes

| Mode | How | Scope | Notes |
|------|-----|-------|-------|
| **STATIC** | Declared in CaseDefinition YAML | Case-scoped | Known at case design time |
| **PROVISIONED** | WorkerProvisioner SPI fires when no static match found | Case-scoped | On-demand agent spawning |
| **SELF_REGISTERED** | External agent discovers case and offers capabilities | Global | Pull model — cases attract agents |

**SELF_REGISTERED is architecturally significant:** agents don't just receive assignments — they can discover running cases and offer their capabilities. CaseHub coordinates knowledge, not just agents.

### 4.4 Choreography vs Orchestration

| Mode | Mechanism | Use when |
|------|-----------|---------|
| **Choreography** | `CaseContextChangedEventHandler` evaluates all bindings reactively | Work is parallel, conditional, emergent |
| **Orchestration** | `WorkOrchestrator.submitAndWait()` — WAITING state, durable via `PendingWorkRegistry` | Sequential dependency, human approval, decision that branches the case |

Both modes coexist within a single case. No pre-commitment to one or the other.

### 4.5 The Async Reactive Loop

```
Worker completes → writes outcome to CaseContext (blackboard)
  → fires CaseContextChangedEvent (async CDI, non-blocking)
  → CaseContextChangedEventHandler re-evaluates ALL bindings
  → satisfied bindings fire WorkerScheduleEvent simultaneously
  → workers provisioned in parallel
  → workers complete → loop
```

**Driven by state, not time.** No polling. No patrol cycles. Zero latency between completion and next scheduling decision. Multiple workers fire simultaneously if multiple bindings are satisfied by one context update.

---

## 5. Gastown Architecture — Deep Detail

### 5.1 Agent Role Taxonomy

| Role | Level | Ephemeral? | Purpose |
|------|-------|------------|---------|
| Mayor | Town | No | Global coordinator, convoy management |
| Deacon | Town | No | Cross-rig daemon, patrol watchdog |
| Boot/Dog | Town | No | Validates Deacon, infrastructure maintenance |
| Witness | Rig | No | Per-rig monitoring of polecats and refinery |
| Refinery | Rig | No | Merge queue processor (Bors-style) |
| Polecat | Rig | Yes | AI agent worker, persistent identity via bead |
| Crew | Rig | No | Long-lived human workspace |

### 5.2 Three-Tier Escalation

| Severity | Actions |
|----------|---------|
| MEDIUM (P2) | Creates bead, sends mail to Mayor |
| HIGH (P1) | Bead + mail + email to Mayor and human contacts |
| CRITICAL (P0) | Bead + mail + email + SMS simultaneously |

Stale escalations auto-re-escalate after 4 hours (configurable), bumping severity.

### 5.3 Wasteland Federation

| Feature | Detail |
|---------|--------|
| Identity | Rig handles = DoltHub usernames (portable across organizations) |
| Attestation | Multi-dimensional: quality, reliability, creativity |
| Reputation | Stamps travel across wastelands (federation via DoltHub) |
| Mode | Currently "wild-west" — local fork claims reconciled via upstream PRs |

---

## 6. Direct Comparison Tables

### 6.1 Core Concept Mapping

| Concept | Gastown | CaseHub | Notes |
|---------|---------|---------|-------|
| Work unit | Bead (6-stage lifecycle) | WorkItem (10-status) + CaseInstance | WorkItem = human task; CaseInstance = process |
| Work bundle | Convoy | CasePlanModel | Convoy is flat; CasePlanModel has goals/milestones/stages |
| Workflow | Formula (TOML) | CaseDefinition YAML + Quarkus Flow | CaseHub additionally has binding conditions |
| Durable workflow | Molecule (bead chains) | Quarkus Flow as worker type | Both survive restarts; quarkus-flow is more formal |
| Work queue | Hook | WorkItem inbox (casehub-work) | Hooks are per-agent; WorkItem inbox is per-human |
| Assignment | `gt sling` | WorkBroker + WorkerSelectionStrategy | CaseHub routing is algorithmic and trust-weighted |
| Messaging | `gt nudge` | qhorus `send_message` (9 speech-act types) | Gastown: informal. CaseHub: formal semantics |
| Obligation tracking | None | qhorus Commitment (7-state lifecycle) | Gastown has no normative commitment concept |
| Audit | Dolt history + OTel | Merkle MMR + LedgerAttestation | Different trust models (see section 6.4) |
| Trust | Wasteland stamps (manual) | Bayesian Beta + EigenTrust (algorithmic) | Gastown: human-curated; CaseHub: auto-computed |
| Federation | Wasteland (DoltHub) | Planned: TrustExportService/TrustImportService | CaseHub model doesn't exist yet |
| Merge queue | Refinery (Bors-style) | Not yet built | casehub-refinery planned as native module |
| Agent monitoring | Witness/Deacon/Boot hierarchy | qhorus Watchdog + stalled obligations | Gastown has recovery; CaseHub has detection only |
| Rate limiting | Scheduler (API rate limit protection) | Not yet built | Significant gap at scale |
| Human tasks | Bead assigned to human | WorkItem lifecycle (SLA, delegation, forms) | CaseHub significantly more capable |
| Persistence | Single Dolt server per town | Named datasources per concern, SPI-pluggable | CaseHub: multi-tenant, pluggable |
| Auth | Not in framework (deployer's concern) | WebAuthn + X-Api-Key (claudony) | Gastown same — auth at gateway |
| CLI | `gt` (rich, comprehensive) | REST APIs + MCP tools | Gastown significantly ahead operationally |

### 6.2 Coordination Model

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Driving force | Agent processes hook → GUPP | Context change → binding evaluation |
| Timing model | Polling (patrol cycles, 5-min checks) | Reactive (event-driven, zero-latency) |
| Parallelism | Explicitly declared in formula | Automatically exploited from binding evaluation |
| Sequencing | Formula order + convoy structure | Binding conditions (JQ/lambda) over blackboard |
| Adaptability | Agent-side reasoning | Engine re-evaluates with updated context |
| Deadlock detection | Witness timeout on stuck agents | Case-level stall detection (no bindings fire, goals unsatisfied) |
| Orchestration mode | None (workflow only) | WAITING state with durable PendingWorkRegistry |
| Cross-agent coordination | Hooks + nudges | qhorus typed channels + commitments |

### 6.3 Worker / Agent Model

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Worker types | Agent only (polecat, crew, dog) | Lambda + Workflow + Agent + Hybrid |
| Worker discovery | Assigned by Mayor/formula | Static declaration / on-demand provisioning / SELF_REGISTERED |
| Worker selection | Hook-based (work goes to assigned agent) | WorkBroker + pluggable WorkerSelectionStrategy |
| Trust-based routing | No (stamps don't drive routing automatically) | Yes (TrustScoreRoutingPublisher → WorkerSelectionStrategy) |
| Worker identity | Session-scoped bead handle | Persistent persona (`{model-family}:{persona}@{major}`) |
| Session persistence | Dolt bead survives session end | Ledger entry survives session end; trust accumulates |
| Capability matching | Formula capability tags | YAML capability tags + semantic matching (SemanticWorkerSelectionStrategy) |
| Concurrency control | Scheduler (API rate limit protection) | Not yet built |
| Recovery on failure | Witness detects + re-assigns | WorkerStatusListener SPI + (planned) RecoveryPolicy |

### 6.4 Audit & Accountability

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Audit mechanism | Dolt git-for-SQL history | Merkle Mountain Range (quarkus-ledger) |
| Trust model | Admin-trusted (trust the Dolt server) | Cryptographic (inclusion proofs, no server trust required) |
| Tamper evidence | Git history (rewritable by admin) | Merkle proof (independently verifiable) |
| Inclusion proofs | No | Yes — Ed25519 signed checkpoints |
| Provenance standard | None (OTel telemetry only) | W3C PROV-DM JSON-LD export |
| Causal chain | Implicit in bead history | Explicit `causedByEntryId` on every entry |
| GDPR Art.17 erasure | No | LedgerErasureService + ActorIdentityProvider SPI |
| GDPR Art.22 decisions | No | ComplianceSupplement (structured automated decision records) |
| EU AI Act Art.12 | No | ComplianceSupplement + LedgerRetentionJob |
| PII sanitisation | No | DecisionContextSanitiser SPI |
| External audit | Cannot prove to third party without trusting server | Merkle proof verifiable by any party independently |

### 6.5 Trust & Reputation

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Model | Stamps (human-curated, multi-dimensional) | Bayesian Beta (auto-computed from attestation history) |
| Transitivity | None stated | EigenTrust (provably resistant to sybil/collusion) |
| Temporal decay | None | Exponential decay weighting (recent evidence counts more) |
| Auto-computation | No (human assigns stamps) | Yes (TrustScoreJob runs nightly) |
| Routing integration | No (manual assignment using stamp knowledge) | Yes (TrustScoreRoutingPublisher → WorkerSelectionStrategy) |
| New agent baseline | No prior = unknown | Beta(1,1) = 0.5 (uniform prior — neither trusted nor distrusted) |
| Cross-deployment | Yes (Wasteland stamps federated via DoltHub) | Not yet (TrustExportService/TrustImportService planned) |
| Mathematical grounding | None — intuitive | Beta distribution (conjugate prior for Bernoulli), EigenTrust (Kamvar et al. 2003) |
| Sybil resistance | None — stamp collusion possible | EigenTrust eigenvector computation resists collusion |

### 6.6 Normative Layer

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Communication semantics | `gt nudge` (notify), `gt sling` (assign) — 2 informal types | 9 speech-act types (QUERY, COMMAND, RESPONSE, STATUS, DECLINE, HANDOFF, DONE, FAILURE, EVENT) |
| Theoretical basis | None — pragmatic evolution | Searle speech act theory, Von Wright deontic logic, Lewis social commitment semantics |
| Obligation tracking | None | Commitment (7-state: OPEN→ACKNOWLEDGED→FULFILLED/DECLINED/FAILED/DELEGATED/EXPIRED) |
| Obligation completeness | 2 of 5 illocutionary categories covered | All 5 categories covered (assertives, directives, commissives, expressives, declarations) |
| Commitment audit | None | MessageLedgerEntry for all 9 types, causal chain via correlationId |
| Trust feedback | None | FULFILLED → LedgerAttestation (SOUND) → TrustScoreJob (planned — finding #1) |
| Stalled obligation detection | Witness timeout on stuck agent | `list_stalled_obligations` MCP tool, WatchdogEvaluationService |

### 6.7 Human-in-the-Loop

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Human task model | Bead assigned to human (same as agent) | WorkItem (dedicated 10-status lifecycle) |
| SLA enforcement | None | expiresAt + claimDeadline + ExpiryCleanupJob + ClaimDeadlineJob |
| Business hours | None | BusinessCalendar SPI |
| Delegation | None | WorkItem DELEGATED status + EscalationPolicy |
| Form schemas | None | formSchemaId + formPayload on WorkItem |
| Spawn (parallel human tasks) | None | WorkItemSpawnGroup with completion rollup |
| Escalation policy | Three-tier severity (MEDIUM/HIGH/CRITICAL) | EscalationPolicy SPI (pluggable per scenario) |
| Case integration | No automatic signal to orchestrator | casehub-work-adapter (WorkItemLifecycleEvent → PlanItem transition) |
| Human interjection mid-case | None | Planned: Qhorus human message → case signal |

### 6.8 Extensibility

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Extension model | Plugin system (formula overlays, role directives, gate types) | SPI-based (Java interfaces, CDI, Quarkus augmentation) |
| Worker extension | Add new polecat/crew type | Lambda / Workflow / Agent / any WorkerExecution impl |
| Binding extension | Formula overlays | JQ / lambda / Drools / any condition evaluator SPI |
| Persistence extension | None (single Dolt server) | WorkItemStore SPI + MongoDB/Redis/etc. alternatives |
| Channel extension | None | CaseChannelProvider SPI (Qhorus, Kafka, email, etc.) |
| Selection extension | None | WorkerSelectionStrategy SPI (least-loaded, semantic, trust-weighted) |
| Notification extension | None | Connector SPI (casehub-connectors) |
| Auth extension | None | Quarkus security extensions (OIDC, JWT, WebAuthn) |
| Ecosystem | Go stdlib + 27 dependencies (closed) | Full Quarkiverse (Kafka, Redis, gRPC, GraphQL, Micrometer, Elasticsearch, etc.) |
| Compile-time verification | No | Yes — Quarkus augmentation verifies all SPI wiring at build time |
| Native image | Go binary (fast) | GraalVM native image (0.084s startup, no JVM, zero reflection overhead) |

---

## 7. CaseHub Advantages — Detailed

### 7.1 The ACM / Blackboard Advantage (Architectural)

The choreography engine's async reactive loop is state-driven, not time-driven:
- **No polling** — bindings evaluate on state change, not on schedule
- **Automatic parallelism** — all simultaneously satisfied bindings fire at once
- **Emergent paths** — the optimal execution path emerges from what is known, not from what was anticipated
- **Adaptive recovery** — failure is a fact on the blackboard; alternative bindings may fire
- **Case-level deadlock detection** — a fixed point with unsatisfied goals is structurally detectable

Gastown cannot detect "this case is structurally impossible to complete given current state" without agent reasoning. CaseHub detects it mechanically.

### 7.2 Binding System (Structural)

The binding system is already infinitely extensible at both condition and execution level:

| Layer | What's pluggable | Current implementations |
|-------|-----------------|------------------------|
| Condition evaluation | Any predicate over CaseContext | JQ expressions, Java lambdas |
| Worker execution | Any WorkerExecution implementation | Lambda, Quarkus Flow workflow, AI agent |
| Worker selection | WorkerSelectionStrategy SPI | LeastLoadedStrategy, SemanticWorkerSelectionStrategy |
| Worker registration | STATIC / PROVISIONED / SELF_REGISTERED | All three modes simultaneously |

### 7.3 Closed Feedback Loop (Unique to CaseHub)

Once platform audit finding #1 is resolved (commitment outcomes → LedgerAttestation):

```
Prescriptive (casehub-engine)  →  assigns work to agent
Normative (qhorus)             →  agent acknowledges and fulfills commitment
Evaluative (quarkus-ledger)    →  commitment outcome writes attestation → trust updated
Prescriptive (casehub-engine)  →  updated trust score drives next assignment
```

This loop is **self-improving without human intervention**. No rules updated. No stamps assigned. Trust accumulates from evidence. Gastown has no mechanism to close this loop automatically.

### 7.4 Formal Mathematical Grounding

| Concept | Mathematical basis | Properties |
|---------|-------------------|------------|
| Trust scoring | Bayesian Beta distribution | Optimal belief update for binary outcomes; conjugate prior for Bernoulli |
| Transitive trust | EigenTrust (Kamvar et al. 2003) | Sybil-resistant; collusion-resistant; converges to true reliability |
| Temporal weighting | Exponential decay | Recent evidence counts more; old evidence fades |
| New agent prior | Beta(1,1) = Uniform(0,1) | Neither trusted nor distrusted — evidence required |
| Speech acts | Searle (1969) illocutionary taxonomy | Complete classification — covers all possible communicative acts |
| Obligations | Von Wright deontic logic | Formal semantics for obligation, permission, prohibition |

### 7.5 Compliance Capabilities

| Standard | CaseHub | Gastown |
|----------|---------|---------|
| GDPR Art.17 (right to erasure) | LedgerErasureService + ActorIdentityProvider SPI | None |
| GDPR Art.22 (automated decision records) | ComplianceSupplement | None |
| EU AI Act Art.12 (logging requirements) | ComplianceSupplement + LedgerRetentionJob | None |
| PII sanitisation | DecisionContextSanitiser SPI | None |
| Cryptographic tamper proof | Merkle Mountain Range, Ed25519 checkpoints | None (Dolt history = admin-trusted) |
| Independent verification | Merkle inclusion proof verifiable without server | Impossible without access to Dolt |
| W3C PROV-DM lineage | LedgerProvExportService | None |

---

## 8. Gastown Advantages — Operational Maturity

These are engineering maturity advantages from having been in production, not architectural advantages. CaseHub needs to build these natively using its own primitives — not adopt from Gastown.

| Capability | Gastown | CaseHub Gap | Build approach |
|-----------|---------|-------------|----------------|
| **Hierarchical watchdog with recovery** | Witness → Deacon → Boot (detect + recover) | qhorus Watchdog detects; no recovery automation | Extend WorkerStatusListener SPI with RecoveryPolicy SPI |
| **Agent concurrency control** | Scheduler prevents API rate limit exhaustion | WorkerProvisioner spawns without throttle | Add SpawnThrottle to ClaudonyConfig |
| **Merge queue management** | Refinery (Bors batch-then-bisect) | Not built | casehub-refinery module: CasePlanModel for MR batch + binding conditions for bisect |
| **Cross-deployment reputation** | Wasteland stamps (federated via DoltHub) | Trust is per-deployment | Extend quarkus-ledger with TrustExportService/TrustImportService SPI |
| **Rich CLI** | `gt` (comprehensive, production-grade) | REST APIs + MCP tools only | Not a priority — MCP is the interface for AI agents |
| **Production dashboard** | Web dashboard + `gt feed` + `gt problems` | Basic claudony dashboard | Extend claudony three-panel dashboard |
| **Formula overlays / role directives** | Runtime customisation of agent instructions | WorkerContextProvider is code, not config | Extend CaseDefinition YAML with overlay mechanism |
| **Cross-rig routing** | routes.jsonl transparent bead routing | Single-deployment scope | Multi-deployment claudony fleet + case routing |

**Important corrections from initial analysis:**
- ~~Durable workflow chains~~ — **Not a gap.** Quarkus Flow as a worker type provides durable workflow chains natively.
- ~~Plugin system~~ — **Not a gap.** Worker model is already pluggable via lambdas + workflows + agents + SPIs + full Quarkus ecosystem. The Drools binding evaluator is reachable without new infrastructure.

---

## 9. Gaps in Both Directions

### 9.1 What CaseHub Needs to Build (from Gastown analysis)

Ordered by severity:

| Priority | Gap | Impact | Build approach |
|----------|-----|--------|----------------|
| 1 | Agent concurrency / spawn throttling | At scale (20-50 agents), API rate limits hit with no back-pressure | SpawnThrottle in ClaudonyWorkerProvisioner |
| 2 | Hierarchical watchdog with recovery | Stuck agents require manual intervention | RecoveryPolicy SPI on WorkerStatusListener |
| 3 | Merge queue management | No code quality gate for AI-generated code | casehub-refinery module (native, on CaseHub primitives) |
| 4 | Cross-deployment trust federation | Trust doesn't accumulate across organizations | TrustExportService / TrustImportService in quarkus-ledger |
| 5 | Formula composition (overlay mechanism) | Case definitions must be forked to customise individual steps | CaseDefinition YAML overlay extension |

### 9.2 What Gastown Lacks (CaseHub → Gastown)

| Category | CaseHub capability | Gastown gap |
|----------|-------------------|-------------|
| Paradigm | Adaptive Case Management (goals, emergent paths) | Workflow only (fixed structure) |
| Binding | JQ + lambda + CloudEvents + schedule triggers | Formula steps only |
| Worker types | Lambda + workflow + agent + hybrid | Agent only |
| Worker registration | Static + provisioned + self-registered (pull model) | Assigned only (push model) |
| Normative layer | 9 speech-act types + 7-state commitment lifecycle | nudge (notify) + sling (assign) — informal |
| Obligation audit | MessageLedgerEntry for all interactions, causal chain | None |
| Trust model | Bayesian Beta + EigenTrust (algorithmic, auto-computed) | Stamps (manual, multi-dimensional) |
| Trust routing | Automatic routing based on computed trust scores | Manual assignment using stamp knowledge |
| Tamper evidence | Merkle inclusion proofs (independently verifiable) | Dolt history (admin-trusted) |
| GDPR/compliance | Art.17, Art.22, Art.12, PII sanitisation | None |
| W3C PROV-DM | LedgerProvExportService | None |
| Human tasks | WorkItem lifecycle (SLA, delegation, forms, escalation) | Beads assigned to humans (no differentiation) |
| Closed feedback loop | Commitment → attestation → trust → routing (when wired) | No automatic feedback from outcomes to routing |
| Extensibility | Full SPI model + Quarkus ecosystem | Plugin system (workflow-level only) |
| Compile-time safety | Quarkus augmentation verifies all SPIs at build time | Runtime plugin loading |
| Formal case model | Goals, milestones, stages, entry conditions | Convoys (flat bundles) |
| Durable workflows | Quarkus Flow as worker type (CNCF Serverless Workflow) | Molecules (bead chains, less formal) |
| Deadlock detection | Case-level stall (fixed point with unsatisfied goals) | Agent-level timeout only |

---

## 10. CaseHub Internal Platform Coherence Audit

Systematic cross-capability analysis across all CaseHub repos. 32 findings. **Individual issues created for top 8.**

Full audit: [casehub-parent#4](https://github.com/casehubio/parent/issues/4)

### Top 8 (individual issues created)

| # | Finding | Repos | Issue |
|---|---------|-------|-------|
| 1 | Commitment terminal states don't write LedgerAttestation — trust scoring has no normative signal | qhorus, ledger | [qhorus#123](https://github.com/casehubio/qhorus/issues/123) |
| 2 | ActorType derivation uses 4 different logics — same actor gets different ActorType across repos | ledger, work, qhorus, engine | [ledger#47](https://github.com/casehubio/ledger/issues/47) |
| 3 | Two parallel delivery SPIs (casehub-connectors + casehub-work-notifications) with overlapping Slack/Teams | connectors, work | [parent#5](https://github.com/casehubio/parent/issues/5) |
| 4 | Qhorus instanceId and ledger actorId unjoined — trust doesn't accumulate across sessions of same persona | qhorus, ledger, claudony | [qhorus#124](https://github.com/casehubio/qhorus/issues/124) |
| 5 | Cross-repo causal chain broken — no causedByEntryId linking MessageLedgerEntry → CaseLedgerEntry → WorkItemLedgerEntry | claudony, engine, qhorus | [claudony#94](https://github.com/casehubio/claudony/issues/94) |
| 6 | PropagationContext.traceId is UUID, OTel trace ID is W3C hex — case spans not correlatable in Jaeger | engine, ledger | [engine#185](https://github.com/casehubio/engine/issues/185) |
| 7 | CaseHub work assignments don't create Qhorus COMMITMENTs — normative obligation lifecycle bypassed entirely | engine, qhorus, claudony | [engine#186](https://github.com/casehubio/engine/issues/186) |
| 8 | No SLA propagation from case budget to child WorkItems or Commitments | engine, work, qhorus | [parent#6](https://github.com/casehubio/parent/issues/6) |

### Four Structural Themes

| Theme | Findings | Impact |
|-------|----------|--------|
| **Normative↔evaluative disconnect** | 1, 7, and 10+ | Prescriptive/normative/evaluative layers designed correctly but not wired — the closed feedback loop doesn't close |
| **Actor identity fragmentation** | 2, 4 | Trust doesn't accumulate across sessions or consistently across repos |
| **Notification silo** | 3 and related | Three event sources need to notify humans — none reach human-facing channels |
| **Cross-repo causal chain broken** | 5, 6 | PROV-DM lineage complete within repos, broken at every boundary |

---

## 11. Strategic Direction

### 11.1 The Market Position

| Market | Right choice | Why |
|--------|-------------|-----|
| Startup / internal tooling / software engineering at speed | Gastown | Operational, in production, rich CLI, federation — one focused domain |
| Enterprise / regulated / AI Act / GDPR / formal accountability | CaseHub foundation | Compliance, cryptographic proof, formal semantics, correct paradigm |
| AI coding agent coordination (enterprise) | CaseHub + casehub-refinery | Foundation accountability + domain application on top |
| Healthcare / legal / financial AI workflows | CaseHub + domain app | Foundation reused; domain-specific application layer built separately |
| AI coordination with complex adaptive branching | CaseHub | ACM paradigm handles emergent paths; workflow cannot |
| Cross-organizational agent reputation | Gastown (today) | Wasteland federation exists; CaseHub federation planned |

**The key strategic point:** Gastown competes with `casehub-refinery` (the AI coding agent application). It does not compete with CaseHub's foundation — Gastown has no equivalent to the normative layer, trust model, compliance features, or human task lifecycle. CaseHub's foundation enables application domains that Gastown cannot reach.

### 11.2 Build vs Adopt Decision

**Never adopt from Gastown.** The overlap is too high and the architectures are incompatible. Every Gastown capability CaseHub needs can be built on CaseHub's own primitives — more cohesively and with the full accountability stack attached.

| Gastown capability | CaseHub approach |
|-------------------|-----------------|
| Hierarchical watchdog | Extend WorkerStatusListener SPI + RecoveryPolicy SPI |
| Concurrency control | SpawnThrottle in ClaudonyWorkerProvisioner |
| Merge queue | casehub-refinery module (CasePlanModel + binding conditions) |
| Cross-deployment reputation | TrustExportService/TrustImportService in quarkus-ledger |
| Formula overlays | CaseDefinition YAML overlay extension |

**The only "near-zero overlap" candidate: merge queue.** CaseHub has zero concept of code review gates, CI integration, or branch merging. A `casehub-refinery` module built on CaseHub's own primitives would be genuinely additive. This is the one case where Gastown's design can inform CaseHub's without adopting any of Gastown's code.

### 11.3 Priority Order

Fix internal coherence issues before adding capability breadth. Gastown's operational experience shows what breaks at scale — these four break first:

| Priority | Action | Why |
|----------|--------|-----|
| 1 | Fix normative→trust wiring (platform audit #1) | The closed feedback loop doesn't close without it |
| 2 | Fix actor identity fragmentation (audit #2, #4) | Trust doesn't accumulate — every session starts blind |
| 3 | Build agent concurrency throttling | First thing that breaks at 10+ simultaneous cases |
| 4 | Build hierarchical watchdog with recovery | Stuck agents require manual intervention at scale |
| 5 | casehub-refinery module | The one genuinely additive Gastown-inspired capability |

---

## 11.4 Implementation Roadmap — Prioritised Build List

*The "order of pain" — what breaks first as CaseHub moves toward real agent counts, ordered by when it becomes a hard blocker. Gastown hit all four of these in production; that is why it built Witness/Deacon/Boot, the Scheduler, and GUPP.*

---

### P0 — Breaks immediately with multiple agents (wiring issues, not new builds)

These are completion gaps in the existing design, not new features. They must be resolved before anything else.

#### P0.1 — Normative→prescriptive wiring (engine#186)
**Symptom:** CaseHub assigns work to an agent but has no way to tell if the agent acknowledged its assignment or silently failed.
**Root cause:** `WorkerScheduleEvent` provisions a tmux session and opens a Qhorus channel, but never sends a COMMAND — so no commitment is created, no obligation lifecycle runs, no trust signal is generated.
**Fix:** In `CaseContextChangedEventHandler` / `WorkOrchestrator.submit()`, after provisioning, call `channelProvider.postMessage(COMMAND)`. The agent's DONE/FAILURE response then drives the full normative lifecycle automatically.
**Repos:** casehub-engine, claudony-casehub, quarkus-qhorus
**Issue:** [engine#186](https://github.com/casehubio/engine/issues/186)

#### P0.2 — Commitment outcomes→trust scoring (qhorus#123)
**Symptom:** Trust scores never update from agent behaviour. The Bayesian model has no input. Routing is always based on priors.
**Root cause:** `CommitmentService.fulfill()` / `.fail()` update state but never write `LedgerAttestation`. `TrustScoreJob` has no signal.
**Fix:** In `LedgerWriteService.record()`, on terminal commitment message (DONE/FAILURE/DECLINE), write a `LedgerAttestation` against the originating COMMAND entry. DONE → SOUND (confidence 0.7), FAILURE → FLAGGED (confidence 0.6), DECLINE → FLAGGED (confidence 0.4). Confidence values config-driven.
**Repos:** quarkus-qhorus (LedgerWriteService), quarkus-ledger (LedgerAttestation)
**Issue:** [qhorus#123](https://github.com/casehubio/qhorus/issues/123)

#### P0.3 — Actor identity fragmentation (ledger#47, qhorus#124)
**Symptom:** Every new Claude session starts with zero trust, even if it is the same AI persona that has built a strong track record. EigenTrust computes over session IDs, not personas.
**Root cause:** Qhorus `LedgerWriteService` writes `actorId = message.sender` (raw instance ID like `claudony-worker-abc123`). Persona format (`claude:analyst@v1`) never reaches the ledger from Qhorus interactions.
**Fix 1:** Add `ActorTypeResolver` utility to quarkus-ledger — single canonical `actorId` derivation for all consumers. ([ledger#47](https://github.com/casehubio/ledger/issues/47))
**Fix 2:** Add `InstanceActorIdProvider` SPI to quarkus-qhorus — maps Qhorus instance IDs to ledger persona IDs. `claudony-casehub` implements it. ([qhorus#124](https://github.com/casehubio/qhorus/issues/124))
**Repos:** quarkus-ledger, quarkus-qhorus, claudony-casehub

---

### P1 — Breaks at scale (10+ concurrent cases / agents)

New capabilities, but hard blockers before CaseHub can be used at meaningful scale.

#### P1.1 — Agent concurrency throttling *(not yet tracked as an issue)*
**Symptom:** Running 10+ cases simultaneously hits Claude API rate limits with no back-pressure. Provisioner spawns sessions without limit.
**Root cause:** `ClaudonyWorkerProvisioner.provision()` creates tmux sessions unconditionally. No global or per-case ceiling.
**Fix:** Add `SpawnThrottle` to `ClaudonyConfig`:
```properties
claudony.casehub.max-concurrent-workers=20       # global ceiling
claudony.casehub.max-workers-per-case=5          # per-case ceiling
claudony.casehub.spawn-queue-timeout=PT5M        # back-pressure wait
```
When ceiling reached, `provision()` queues the request rather than failing. Queue drains as workers complete. Pure addition to `ClaudonyWorkerProvisioner` — no new abstraction needed.
**Repos:** claudony (ClaudonyWorkerProvisioner, ClaudonyConfig)

#### P1.2 — Hierarchical agent oversight with recovery *(not yet tracked as an issue)*
**Symptom:** A stuck agent requires manual `gt`-equivalent intervention. There is no automated recovery. At 20+ agents this is operationally unsustainable.
**Root cause:** Detection exists (qhorus `list_stalled_obligations`, `WorkerStatusListener.stalled()`) but detection does not trigger recovery — it alerts into a Qhorus channel and stops.
**Three tiers already exist structurally — they just need the recovery action wired in:**

| Tier | Existing component | Gap |
|------|--------------------|-----|
| Worker-level | `WorkerStatusListener.stalled()` in casehub-engine | No recovery action |
| Case-level | qhorus `WatchdogEvaluationService` → `list_stalled_obligations` | Alerts to Qhorus channel only |
| Fleet-level | claudony `PeerHealthScheduler` + session expiry | No case-level signal on session death |

**Fix:** Add `RecoveryPolicy` SPI to casehub-engine `api/spi/`:
```java
public interface RecoveryPolicy {
    RecoveryAction decide(WorkerStalledContext ctx);
}

public enum RecoveryAction { REPROVISION, ESCALATE_TO_HUMAN, CANCEL_CASE, WAIT }
```
`WorkerStatusListener.stalled()` calls `RecoveryPolicy.decide()`. Default implementation: `ESCALATE_TO_HUMAN`. `claudony-casehub` provides a `ReprovisioningRecoveryPolicy` that creates a new tmux session and transfers the channel context. This is a natural extension of existing SPIs, not a new system.
**Repos:** casehub-engine (RecoveryPolicy SPI + default), claudony-casehub (ReprovisioningRecoveryPolicy)

#### P1.3 — Trust routing: make WorkerSelectionStrategy injectable + add TrustWeightedSelectionStrategy *(not yet tracked)*
**Symptom:** Trust scores are computed (after P0) but have zero effect on who receives work. `TrustScoreRoutingPublisher` fires CDI events that no `WorkerSelectionStrategy` in casehub-engine observes. The entire trust model is decorative without this.
**Root cause:** `CaseContextChangedEventHandler` hard-codes `LeastLoadedStrategy` at the call site rather than injecting a strategy. No trust-aware strategy exists.
**Fix:**
1. Make `WorkerSelectionStrategy` injectable in `CaseContextChangedEventHandler` (`@Inject WorkerSelectionStrategy strategy`) rather than instantiating `LeastLoadedStrategy` directly
2. Add `TrustWeightedSelectionStrategy` to casehub-engine (or claudony-casehub): observes `TrustScoreFullPayload` CDI events, applies trust score as a multiplier over workload count when selecting candidates
3. `SemanticWorkerSelectionStrategy` (already in casehub-work-ai) then also becomes usable by casehub-engine for the first time

**Why P1:** Depends on P0 (trust must be computed before it can be consumed), but should follow immediately. Without it the feedback loop from P0 is wired but goes nowhere.
**Repos:** casehub-engine (CaseContextChangedEventHandler, TrustWeightedSelectionStrategy), quarkus-ledger (TrustScoreRoutingPublisher already exists)

#### P1.4 — Merge CaseLedgerEntry branch *(not yet tracked)*
**Symptom:** Case lifecycle events are not in the tamper-evident ledger. The compliance and audit story — CaseHub's primary market differentiator for regulated industries — is incomplete without it.
**Root cause:** `casehub-ledger` module and `CaseLedgerEventCapture` (`@ObservesAsync CaseLifecycleEvent` → writes `CaseLedgerEntry`) exist in `feat/casehub-ledger-integration` branch but are unmerged. The branch has merge conflict markers in `docs/DESIGN.md`.
**Fix:** Resolve merge conflicts, merge the branch to main. Verify: (1) `LedgerTraceListener` propagates correctly to `CaseLedgerEntry` via JPA `@EntityListeners` inheritance, (2) a case lifecycle event produces a verifiable Merkle entry, (3) `EventLog` (operational) and `CaseLedgerEntry` (compliance) co-exist without drift — add invariant test confirming every `CaseLedgerEntry` has a matching `EventLog` entry.
**Why P1:** Gastown makes this case too clearly — if CaseHub's compliance story is its market position, case events must be in the ledger. Running in production without this means the most important events (case start, case fault, case complete) have no cryptographic proof.
**Repos:** casehub-engine (casehub-ledger module, feat/casehub-ledger-integration branch)

---

### P2 — Important for production quality, not immediate blockers

#### P2.1 — Cross-deployment trust federation *(not yet tracked)*
**Symptom:** Trust built by an agent in one CaseHub deployment is invisible to another. Cross-organization deployments start from the uniform prior.
**Root cause:** Trust scoring is per-deployment. No import/export mechanism.
**Fix:** Add to quarkus-ledger:
- `TrustExportService` — publishes `ActorTrustScore` deltas in canonical format (actor persona → alpha/beta values + timestamp)
- `TrustImportService` SPI — consumes trust deltas from external source, seeds Bayesian priors
The math already handles this: seeding Beta(α, β) from an external source rather than starting from Beta(1,1) is a one-line change in `TrustScoreJob`. The infrastructure is the data exchange format and transport (webhook, Kafka topic, or pull API).
**Repos:** quarkus-ledger

#### P2.2 — OTel trace alignment (engine#185)
**Symptom:** Case spans not correlatable in Jaeger/Grafana. `PropagationContext.traceId` is a UUID; OTel span ID is W3C hex. Same case, different trace IDs.
**Fix:** Populate `PropagationContext.traceId` from `LedgerTraceIdProvider.currentTraceId()` at case creation instead of `UUID.randomUUID()`.
**Repos:** casehub-engine
**Issue:** [engine#185](https://github.com/casehubio/engine/issues/185)

#### P2.3 — Cross-repo causal chain (claudony#94)
**Symptom:** W3C PROV-DM lineage breaks at every repo boundary. Cannot trace a case → its Qhorus messages → its WorkItems as a unified graph.
**Fix:** `ClaudonyWorkerProvisioner.provision()` captures the active `MessageLedgerEntry.id` and passes it as `causedByEntryId` for the first `CaseLedgerEntry`. Requires `CaseLineageQuery` JPA implementation (currently `EmptyCaseLineageQuery`).
**Repos:** claudony-casehub, casehub-engine
**Issue:** [claudony#94](https://github.com/casehubio/claudony/issues/94)

---

### P3 — Capability expansion (after foundations are solid)

#### P3.1 — casehub-refinery (first application layer)
Not a module in casehub-engine — a separate **application** built on top of CaseHub's foundation. This is the pattern for all domain-specific CaseHub apps: the foundation provides the primitives; the application wires them into a domain.

`casehub-refinery` is the AI coding agent coordination application. The foundation knows nothing about git, PRs, or CI — that's the application's concern:

| What the app provides | Built on which CaseHub primitive |
|----------------------|----------------------------------|
| Merge queue as a process | `CasePlanModel` — each case is a batch of MRs |
| MR human review step | `WorkItem` with SLA + form schema |
| Automated CI / lint / security check | Lambda worker or Quarkus Flow workflow |
| Batch-then-bisect strategy | Choreography binding: tip-of-batch fails → binding condition routes to bisect sub-case |
| Agent-to-agent review communication | qhorus typed channels |
| Failure notification | casehub-connectors Slack/Teams delivery |

This establishes the **application layer pattern** for CaseHub. Other planned applications (healthcare, legal, financial) follow the same pattern — each is a separate repo that uses the foundation's primitives without modifying them. No Gastown code adopted.

#### P3.2 — SLA propagation (parent#6)
Case budget bounds child WorkItem and Commitment deadlines. Currently a 1-hour case can spawn a 48-hour WorkItem. Adapter-level fix in `casehub-work-adapter`.
**Issue:** [casehub-parent#6](https://github.com/casehubio/parent/issues/6)

#### P3.3 — Notification consolidation (parent#5)
`casehub-work-notifications` Slack/Teams implementations replaced with `casehub-connectors` delegation. Unblocks: stalled commitment alerts, case fault notifications, escalation notifications — all via one outbound pipeline.
**Issue:** [casehub-parent#5](https://github.com/casehubio/parent/issues/5)

#### P3.4 — Human-in-the-loop end-to-end (casehub-work-adapter completion) *(not yet tracked)*
**Symptom:** CaseHub can create human tasks (WorkItems) but cannot use their outcomes to progress a case. A human completing a WorkItem does not resume a waiting case. The HITL loop is open.
**Root cause:** `casehub-work-adapter` exists and bridges `WorkItemLifecycleEvent → PlanItem` transitions via choreography, but it is incomplete and blocked on casehub-engine stability. The `WAITING` state orchestration path (human approval → case resumes) has no end-to-end test.
**Fix:** Complete `casehub-work-adapter`: (1) `WorkItemLifecycleEvent(COMPLETED)` with a `callerRef` encoding `case:{id}/pi:{planItemId}` fires `CaseHubReactor.signal()`, (2) the case engine transitions the plan item from WAITING to active, (3) binding re-evaluation fires next workers. This is the primary HITL path — without it CaseHub cannot orchestrate any process that requires human judgment mid-case.
**Repos:** casehub-engine (casehub-work-adapter module), casehub-work

#### P3.5 — Notification for critical operational events *(not yet tracked)*
**Symptom:** Stalled obligations, case faults, and WorkItem escalations surface in Qhorus channels only. Operations teams have no external signal when things go wrong.
**Root cause:** Alert events exist (qhorus `WatchdogEvaluationService`, casehub-engine `FAULTED` state, casehub-work `EscalationPolicy`) but none route through `casehub-connectors` to human-facing channels.
**Depends on:** P3.3 (notification consolidation) — needs the unified delivery pipeline first.
**Fix:** Wire three event sources to `casehub-connectors`:
- `WatchdogEvaluationService` stall detection → `Connector.send()` (Slack/email)
- `CaseLifecycleEvent(FAULTED)` → `Connector.send()` (email/SMS for critical cases)
- `EscalationPolicy.escalate()` in casehub-work → `Connector.send()` (Slack for MEDIUM, email for HIGH, SMS for CRITICAL)
**Repos:** quarkus-qhorus, casehub-engine, casehub-work, casehub-connectors

---

### Summary Build Order

| Phase | Items | Gate to next phase |
|-------|-------|-------------------|
| **P0 — Wiring** | engine#186, qhorus#123, ledger#47, qhorus#124 | Normative layer functional end-to-end; trust accumulates from real behaviour |
| **P1 — Scale** | Concurrency throttle, RecoveryPolicy SPI, trust routing wired, CaseLedgerEntry merged | Can run 10+ agents; trust actually drives routing; case events in ledger |
| **P2 — Quality** | OTel alignment, causal chain, trust federation | Full observability; audit trail complete; cross-deployment trust |
| **P3 — Expand** | casehub-refinery, SLA propagation, notification consolidation, HITL end-to-end, critical event notifications | New capabilities on a solid foundation |

---

## 12. Technology Stack Comparison

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Language | Go 1.25+ | Java 21 (on Java 26 JVM) |
| Persistence | Dolt SQL Server (git semantics) | PostgreSQL / H2 (Flyway managed, SPI-pluggable) |
| Runtime | Go binary (no runtime) | GraalVM native image (0.084s startup) or JVM |
| Reactive model | Goroutines + patrol polling | Vert.x event loop + Mutiny reactive streams |
| Workflow | Formula (TOML) + Molecules | Quarkus Flow (CNCF Serverless Workflow SDK) |
| Rules | None (agent cognition) | JQ + lambda (+ Drools via SPI) |
| Message protocol | Proprietary (nudge/sling) | qhorus (A2A compatible, MCP tools) |
| Observability | OTel (strong, comprehensive) | OTel (via Quarkus) + Merkle tamper evidence + PROV-DM |
| Distribution | Homebrew + npm + Docker | GitHub Packages (Maven) + Docker |
| Agent support | Claude Code, Copilot, Gemini, Cursor, Codex | Claude Code (claudony), any via WorkerProvisioner SPI |
| IDE/CLI | Rich `gt` CLI | MCP tools + REST APIs |
| Version | v1.0.1 (production) | Pre-production (active development) |

---

## 13. Corrections and Additions (Post-Review)

*Systematic second-pass review findings — corrections to the analysis above.*

### 13.1 Factual Corrections

| Item | Original claim | Correction |
|------|---------------|-----------|
| Gastown plugin system | "Formula overlays / role directives" | Plugin system is far more sophisticated: executable, stateful, patrol-driven extensibility with 5 gate types (cooldown, cron, condition, event, manual), dog-dispatched execution, wisp-based history. Role directives and formula overlays are separate mechanisms. |
| Gastown failure recovery | "Witness timeout on stuck agents" | Witness monitors polecats, Deacon monitors cross-rig, Boot validates Deacon every 5 min. Hierarchical recovery, not just timeout detection. |
| Gastown binding flexibility | "Formula steps only" | Plugin event gates enable custom-signal dispatch — closer to event-driven than pure formula steps. |
| Gastown API rate limiting | Same layer as CaseHub gap | Gastown's Scheduler is per-agent-session; CaseHub's gap is per-worker-spawn-throttle. Different layers. |
| Paradigm difference sharpness | "Gastown is pure workflow" | Gastown's plugin gates approximate quasi-ACM. The paradigm difference is real but less sharp than framed — Gastown is workflow that has grown condition-driven capabilities. |
| Trust model dimensionality | "Stamps (manual) vs Bayesian Beta (auto)" | Wasteland stamps are multi-dimensional (quality, reliability, creativity). Bayesian Beta is unidimensional. Each is incomplete in different ways. |

### 13.2 Gastown Capabilities Understated

| Capability | Detail |
|-----------|--------|
| **OTel data model** | Far richer than "strong, comprehensive" implies. Per-agent `run.id` anchors all events per spawn. Tracks: `agent.instantiate`, `agent.event`, `agent.usage`, `bd.call`, `mail`, `mol.*` (workflow stages), `bead.create` with parent-child relationships. Opt-in verbosity control. |
| **Dolt git-for-SQL** | Time-travel queries (query state at any past commit), branch-and-merge for experimental work, conflict-free concurrent mutation, rollback without admin access. Richer than "auditable history". |
| **`gt seance`** | Agents can query predecessor sessions' decisions. Enables genuine context handover beyond just work state. CaseHub's WorkerContextProvider rebuilds from ledger entries but not predecessor reasoning. |
| **Stale detection + cleanup** | `gt stale` + dog plugins enable automated detection and cleanup of stale artifacts. Operational capability absent from CaseHub. |
| **Convoy lifecycle** | More than a "flat bundle" — Convoy has rollup mechanics, unified visibility across rigs, historical records, and parallel tracking similar to CaseHub milestones. |
| **Plugin gate types** | 5 distinct gate types: cooldown (rate limiting), cron (schedule), condition (state predicate), event (signal-driven), manual (human-triggered). Each dispatches to idle dogs. |
| **Polecat persistent identity** | DoltHub usernames survive restarts. Cumulative work histories and "CV chains" across ephemeral sessions — permanent identity distinct from session identity. |

### 13.3 CaseHub Capabilities Understated

| Capability | Detail |
|-----------|--------|
| **Closed feedback loop novelty** | The normative→trust→routing loop (once wired) is architecturally novel for agent coordination. No existing multi-agent framework auto-routes future work based on cryptographically attested prior performance without human intervention. |
| **Hybrid mode per-case** | Choreography and orchestration can coexist within a single case without pre-commitment. A case can fan out reactively, then wait synchronously for human approval, then resume reactive coordination. No workflow system supports this. |
| **DELEGATED commitment state** | HANDOFF is not a workaround — it's a first-class obligation transfer with full causedByEntryId chain. The new obligor is formally named. The obligation history is complete. Gastown has no equivalent. |
| **ComplianceSupplement specificity** | Not generic logging. Specific fields for EU AI Act Art.12: `algorithmRef`, `confidenceScore`, `contestationUri`, `humanOverrideAvailable`. Purpose-built regulatory compliance. |
| **Merkle tlog-checkpoint publishing** | Ed25519-signed checkpoints can be published to an **external transparency log** and verified without accessing the CaseHub server at all. Gastown's Dolt requires server access for any verification. |
| **EigenTrust sybil resistance** | Eigenvector computation provably resists collusion: a group of agents that systematically attest to each other's reliability cannot inflate their scores beyond what the global network structure supports. Gastown's stamps have no such property. |
| **Three-layer actor identity** | quarkus-ledger defines: persistent identity (stable trust key, persona format), configuration binding (agentConfigHash for forensic config drift detection), session correlation (ephemeral trace ID). More structured than any Gastown identity model. |
| **All four worker types simultaneously** | STATIC + PROVISIONED + SELF_REGISTERED can all be active in the same case. Some workers are pre-declared, some are spawned on demand, some discover the case themselves. No pre-commitment to a single discovery model. |

### 13.4 Missing Comparison Dimensions

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| **Administrative intervention** | Mayor can reassign work; Deacon can restart agents; fine-grained mid-case control | Case cancellation or explicit WorkOrchestrator calls only; coarser granularity |
| **Predecessor session access** | `gt seance` — agents query prior sessions' decisions | WorkerContextProvider rebuilds from ledger entries; no access to prior reasoning |
| **State at scale** | Single Dolt server per town — potential bottleneck | Named datasources allow sharding per concern; SPI-pluggable backends |
| **Audit reconstruction** | `gt seance` + bead history in Dolt | Ledger lineage + causedByEntryId; complete within repos, gaps at boundaries |
| **Failure taxonomy** | Agent fail (Witness), work fail (bead error), system fail (Dolt down) | Binding eval fail vs worker fail vs orchestration timeout — no unified taxonomy |
| **Decision model** | Agents reason (LLM); hooks route work explicitly | Engine reasons (JQ/lambda); bindings route declaratively |
| **Backpressure propagation** | API rate limit via Scheduler at session level | No backpressure yet; SLA propagation from case budget to child items also missing |
| **Multi-agent CLI debugging** | `gt feed`, `gt problems`, `gt peek` | No equivalent debugging surface |

### 13.5 Completion Risk Assessment

The internal coherence issues (#1, #2, #4, #5, #7 in platform audit) are not minor wiring gaps — they represent the **prescriptive, normative, and evaluative layers being designed correctly but not integrated**. Until these are resolved:

- Trust scores don't accumulate from agent behaviour (finding #1, #7)
- The same agent gets different ActorType in different repos (finding #2)
- Trust doesn't persist across sessions for the same persona (finding #4)
- The causal chain breaks at every repo boundary (finding #5)

Gastown avoids this entirely because everything flows through a single Dolt server — one source of truth for all state. CaseHub's distributed, SPI-pluggable persistence model is architecturally superior for extensibility and compliance, but creates these integration gaps by design. Resolving them is a prerequisite for CaseHub's formal accountability model to actually function end-to-end.

**This is not a design flaw — it is a completion risk.** The design is correct. The wiring is incomplete.
