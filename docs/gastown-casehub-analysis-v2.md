# CaseHub vs Gastown: Architectural Analysis

> **Status:** Working document — updated 2026-05-02
> **Date:** 2026-04-27 (last revised 2026-05-02)
> **Version:** v2 — full rewrite with foundation/application separation

---

## 1. Executive Summary

The central architectural difference between CaseHub and Gastown is **layering**. CaseHub is a domain-agnostic coordination foundation on top of which domain-specific applications are built. Gastown is a software engineering coordination application — well-built and in production — with no separable foundation underneath. Its merge queue, rig/worktree model, git/CI integration, and agent lifecycle management are application-layer concerns baked into its infrastructure. You cannot reuse Gastown's coordination layer for regulated compliance or knowledge-intensive case management because the domain is not above the infrastructure — it *is* the infrastructure. CaseHub's foundation has no domain knowledge. The merge queue does not live in `casehub-engine`; it is a future application (`casehub-devtown`) that uses CaseHub's primitives. The same foundation enables 16+ distinct enterprise AI agent use cases — from LLM supervisor mode to regulatory compliance to RAG pipelines to AI-assisted code review — each as a separate application. These are application-layer patterns built on the foundation, not the foundation's own orchestration modes. See [engine#102](https://github.com/casehubio/engine/issues/102).

The second fundamental difference is **orchestration breadth**. Gastown drives agents through predefined steps: formula steps, convoy structure, hook-based dispatch — workflow. CaseHub's foundation supports a full spectrum of orchestration modes, selectable per use case:

| Mode | Mechanism | When to use |
|------|-----------|-------------|
| **Pure choreography** | Bindings fire reactively over shared blackboard (CaseContext) when JQ/lambda conditions are satisfied | Emergent, parallel, adaptive workflows where paths cannot be known upfront |
| **Structured plans** | `CasePlanModel` with typed `PlanItem`s, stage gating, CMMN lifecycle | Work that is well-understood but complex — stages, milestones, goals |
| **Workflow workers** | Quarkus Flow (CNCF Serverless Workflow) as a worker type within a case | Deterministic, durable multi-step processing within a single worker boundary |
| **LLM supervisor mode** | `LlmPlanningStrategy` SPI — an LLM reads CaseContext and selects the next binding dynamically | Open-ended goals where the next step cannot be determined without reasoning over current state |
| **Hybrid** | Any combination of the above within a single case | Complex cases that are partly structured, partly adaptive, partly human-driven |

All five patterns from LangChain4j's agentic AI model (sequential, loop, parallel, parallel mapper, conditional) are expressible — emerging from binding conditions and stage gating rather than explicit pattern selection. The difference is that CaseHub's patterns come with distributed workers, human-in-the-loop as a first-class concern, cryptographic audit, and compliance features that LangChain4j does not address. Gastown competes with `casehub-devtown` — the software engineering application — not with CaseHub's foundation.

---

## 2. The Layering Architecture

CaseHub is explicitly two-tiered:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              Application Layer                                    │
│                                                                                   │
│  casehub-devtown      [domain app]          [domain app]     [domain app]      │
│  (AI-assisted dev,     (e.g. regulated        (e.g. legal      (e.g. financial   │
│   code review,          clinical workflows)    case mgmt)       compliance)       │
│   merge orchestration)                                                             │
│                         ↑ illustrative — the platform enables these;              │
│                           building them requires domain expertise                  │
│                                                                                   │
│  + LLM supervisor mode, RAG pipelines, elastic research teams,                   │
│    saga pattern, sub-case orchestration, multi-modal pipelines...                 │
│    (see engine#102 for 16 planned use-case patterns)                             │
├──────────────────────────────────────────────────────────────────────────────────┤
│                              Foundation Layer                                     │
│                                                                                   │
│  casehub-engine          casehub-qhorus           casehub-ledger                │
│  (ACM engine, binding    (agent mesh, speech       (Merkle audit, Bayesian       │
│   system, blackboard,    acts, commitments,        trust, GDPR, PROV-DM)         │
│   plans, LLM planning    typed channels)                                          │
│   SPI, stage gating)                                                              │
│                          casehub-work              casehub-connectors            │
│                          (human task lifecycle,     (outbound delivery SPI:       │
│                           SLA, delegation)          Slack, Teams, SMS, email)     │
│                                                                                   │
│  + Quarkus ecosystem (Kafka, Redis, gRPC, OIDC, Micrometer, Elasticsearch...)   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

The foundation has no domain knowledge. The engine knows nothing about git, PRs, clinical pathways, or regulatory requirements. It knows about cases, bindings, workers, contexts, goals, and plans. Domain logic lives entirely in the application layer.

Gastown has no equivalent separation:

```
Gastown:
┌────────────────────────────────────────────────────────────────────┐
│  Domain (software engineering) + Infrastructure (merged, inseparable)│
│  Refinery (merge queue) │ rig/worktree model │ git/CI integration   │
│  Mayor │ Deacon │ Boot │ Witness │ Polecat │ Dolt │ gt CLI          │
└────────────────────────────────────────────────────────────────────┘
```

Gastown's merge queue is not an application built on a foundation — it is infrastructure. Its rig/worktree model is not pluggable — it is the coordination unit. Its git/CI integration is not an adapter — it is load-bearing. These are application-layer concerns baked into what would otherwise be the foundation layer. This is not a criticism of Gastown's design for its target domain; it is an accurate description of what can and cannot be reused from it. Gastown can only ever be one application.

---

## 3. System Overviews

### 3.1 CaseHub Ecosystem

| Repo | Purpose | Tier |
|------|---------|------|
| `casehub-parent` | BOM, CI dashboards, full-stack build | — |
| `casehub-engine` | Hybrid choreography+blackboard ACM engine | Foundation |
| `casehub-qhorus` | Agent communication mesh (speech acts, commitments, typed channels) | Foundation |
| `casehub-ledger` | Immutable tamper-evident audit ledger, Bayesian trust scoring, GDPR compliance | Foundation |
| `casehub-work` | Human task lifecycle (WorkItem inbox, SLA, delegation, escalation) | Foundation |
| `casehub-connectors` | Outbound message connectors (Slack, Teams, SMS, email) | Foundation |
| `claudony` | Remote Claude CLI sessions, CaseHub SPI implementations, dashboard | Integration |
| `casehub-devtown` *(planned)* | AI coding agent coordination — merge queue, review orchestration | Application |
| `casehub-healthcare` *(planned)* | Clinical workflow application | Application |
| `casehub-legal` *(planned)* | Legal case management application | Application |

**External integrations (Quarkus ecosystem):** `quarkus-flow` (CNCF Serverless Workflow, usable as a worker type), Drools (pluggable binding evaluator via open SPI), and the full Quarkiverse — Kafka, Redis, MongoDB, gRPC, OIDC, Micrometer, Elasticsearch, etc.

### 3.2 Gastown

| Component | Primary concern | Foundation or application? |
|-----------|----------------|---------------------------|
| `Dolt SQL Server` | Single persistent store per town (git semantics for SQL) | Foundation |
| `Mayor` | Global coordinator, convoy management, cross-rig communication | Foundation |
| `Deacon` | Cross-rig daemon watchdog with patrol cycles | Foundation |
| `Boot / Dog` | Validates Deacon every 5 min; infrastructure maintenance workers | Foundation |
| `Witness` | Per-rig monitoring of polecats and refinery | Foundation |
| `gt` CLI | All agent and operator interactions | Foundation |
| `Refinery` | Merge queue (Bors-style batch-then-bisect) | **Application** |
| `Polecat` | Ephemeral AI agent worker, persistent bead identity | Foundation |
| `Crew` | Long-lived human workspace with full git clone | **Application** |
| `Wasteland` | Federated identity and reputation via DoltHub | Foundation |
| `gt-proxy-server` | Sandboxed container execution with mTLS | Foundation |
| `beads` CLI | Atomic work record management | **Application** |

Key abstractions: Bead (atomic work unit, 6-stage lifecycle), Convoy (bundle of related beads), Formula (TOML workflow template), Molecule (durable chained bead workflows), Hook (pinned bead serving as agent work queue), GUPP ("if there is work on your Hook, YOU MUST RUN IT").

---

## 4. Foundation vs Foundation (Apples to Apples)

This section compares only coordination infrastructure — stripping application-layer concerns from Gastown (merge queue, git/CI integration, rig/worktree model, `gt beads` CLI) to get an honest infrastructure-to-infrastructure comparison.

### 4.1 Coordination Model

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Paradigm | Workflow (formula steps, fixed at design time) | Adaptive Case Management (goals, emergent paths) | ACM is the correct paradigm for AI agent coordination where outputs determine next steps |
| Core question | What steps must be executed? | What needs to be achieved? | |
| Driving force | Agent processes hook → GUPP | Context change → binding evaluation | Gastown: agent-initiated. CaseHub: engine-initiated. |
| Timing model | Polling (patrol cycles, 5-min checks) | Reactive (event-driven, zero-latency) | CaseHub fires immediately on state change |
| Parallelism | Explicitly declared in formula | Automatically exploited when multiple bindings satisfied simultaneously | CaseHub: no pre-declaration required |
| Binding trigger types | Formula step order + plugin event gates | JQ predicate / Java lambda / CloudEvents / cron schedule | CaseHub binding conditions are richer and pluggable |
| Sequencing | Formula order + convoy structure | Binding conditions (JQ/lambda) over accumulated blackboard state | |
| Failure handling | Agent-side logic or Witness re-assignment | Engine re-evaluates bindings with updated context; alternative paths fire automatically | CaseHub handles failure as new information on the blackboard |
| Orchestration mode (synchronous wait) | None | WAITING state with durable PendingWorkRegistry | Gastown is workflow-only; no first-class synchronous wait |
| Deadlock detection | Agent-level timeout (Witness) | Case-level stall — fixed point with unsatisfied goals is mechanically detectable | CaseHub detects structural impossibility without agent reasoning |
| Hybrid choreography+orchestration | No | Yes — both coexist per case, no pre-commitment | |
| Theoretical basis | Operational evolution from practice | Hayes-Roth Blackboard Architecture + CMMN standard | |

**Winner: CaseHub.** The ACM/blackboard paradigm is strictly more expressive than workflow for agent coordination. Gastown's plugin event gates approximate condition-driven dispatch but cannot be composed with synchronous orchestration or goal-level termination detection.

### 4.2 Worker / Agent Model

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Worker types | Agent only (polecat, crew, dog) | Lambda + Workflow + Agent + Hybrid — all peers in the binding system | CaseHub can mix deterministic and autonomous workers in one case |
| Worker discovery | Assigned by Mayor or formula | Static / PROVISIONED (on-demand) / SELF_REGISTERED (pull model) | SELF_REGISTERED is architecturally significant: agents discover cases and offer capabilities |
| Worker selection | Hook-based (explicit assignment) | WorkBroker + pluggable WorkerSelectionStrategy SPI | CaseHub selection is algorithmic, trust-weighted, semantic-capable |
| Worker identity | Session-scoped bead handle | Persistent persona (`{model-family}:{persona}@{major}`) | Both survive session end; CaseHub trust accumulates across sessions (once P0.3 resolved) |
| Capability matching | Formula capability tags | YAML capability tags + SemanticWorkerSelectionStrategy | |
| Concurrency control | Scheduler (per-session API rate limit protection) | Not yet built | **Gastown advantage** — significant gap at scale |
| Recovery on failure | Witness detects + re-assigns | WorkerStatusListener SPI + detection; no automated recovery yet | **Gastown advantage** — detection + action vs detection only |

**Mixed.** CaseHub's worker model is more expressive (types, discovery modes, selection). Gastown has operational capabilities (concurrency control, recovery) that CaseHub has not yet built.

### 4.3 Normative Layer

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Communication types | `gt nudge` (notify), `gt sling` (assign) — 2 informal types | 9 speech-act types: QUERY, COMMAND, RESPONSE, STATUS, DECLINE, HANDOFF, DONE, FAILURE, EVENT | |
| Theoretical basis | None — pragmatic evolution | Searle speech act theory, Von Wright deontic logic, Lewis social commitment semantics | |
| Illocutionary completeness | 2 of 5 categories covered | All 5 categories: assertives, directives, commissives, expressives, declarations | |
| Obligation tracking | None | Commitment (7-state: OPEN→ACKNOWLEDGED→FULFILLED/DECLINED/FAILED/DELEGATED/EXPIRED) | Gastown has no normative commitment concept |
| Commitment audit | None | MessageLedgerEntry for all 9 types, causal chain via correlationId | |
| Trust feedback from outcomes | None | ~~FULFILLED → LedgerAttestation (SOUND) → TrustScoreJob — once P0.2 resolved~~ **✅ DONE** — `LedgerWriteService.record()` now writes attestations on DONE/FAILURE/DECLINE (qhorus#123) | |
| Stalled obligation detection | Witness timeout on stuck agent | `list_stalled_obligations` MCP tool, WatchdogEvaluationService | |
| DELEGATED obligation transfer | None | HANDOFF is first-class: new obligor formally named, full causedByEntryId chain preserved | |

**Winner: CaseHub.** This is the sharpest gap. Gastown has no normative layer — communication is informal and obligations are untracked.

#### What this enables for AI-assisted development

**First, the obvious objection: couldn't Gastown just add more status values to a bead?**

Yes. Adding DECLINED, DELEGATED, EXPIRED as enum values to a bead takes an afternoon. That is not what the normative layer is.

The difference is between **tracking** and **accountability**. A status field tracks what state a piece of work is in. A commitment tracks who is *accountable* for it, what they *promised*, and whether they *followed through* — as a formal record between two named parties. When something goes wrong, these answer different questions.

Consider: a bead is marked DONE. Status tracking tells you it's closed. But did the agent actually review it, or did they mark it done to clear their queue? Did they have the information needed to make a sound judgement? Who specifically was responsible — the agent that accepted the assignment, or the one it was silently passed to? A status field cannot tell you any of this. A commitment can, because accountability is structural, not inferred.

This distinction produces concrete operational consequences:

**Failure modes that demand different responses are distinguishable.**

When a Gastown agent doesn't complete a bead, Witness sees a timeout and re-assigns. The cause is unknown — was the agent overloaded? Did it start and hit an error? Did it refuse silently? Did it hand off to someone who also failed? All of these look the same. In CaseHub the commitment state carries the distinction:

| Cause | CaseHub knows | Correct response |
|-------|--------------|-----------------|
| Agent didn't acknowledge | Assignment not accepted — may be overloaded or dead | Check agent health *before* re-routing |
| Agent explicitly can't do it | DECLINED — agent is fine, wrong capability | Re-route immediately |
| Agent started, then failed | FAILED — agent encountered an error | Investigate the agent before re-using |
| Agent passed to a specialist | DELEGATED — chain is intact | Track the new obligor; don't re-assign |
| Deadline passed, no resolution | EXPIRED — systemic problem | Escalate; something is wrong upstream |

At 5 agents this barely matters — you can investigate manually. At 50 agents running overnight across a 200-PR batch, responding correctly to each of these without waking someone up requires that the infrastructure knows the difference. Status fields don't give you that. Accountability does.

**Accountability makes trust scoring meaningful.**

This is where the skeptic's "just add status fields" argument breaks down most clearly. If an agent can mark a bead DONE without formally asserting "I completed what was asked of me," then counting completion rates for trust scoring is meaningless. You're scoring bead closure, not review quality.

With a commitment, DONE is an accountable assertion from a named obligor to a named requester: "I fulfilled what you asked." That assertion is attached to the agent, timestamped, ledgered, and carries weight in the trust model. An agent that marks beads done without doing the work will eventually be caught — a FLAGGED attestation after a missed bug decreases their trust score. Routing shifts accordingly. Nobody configures this. It emerges from the accountability record.

A Gastown fleet with more status values can track completion. It cannot build a self-improving routing system on top of that tracking, because tracking alone has no accountability semantics to score.

**The delegation chain is auditable, not reconstructed.**

An agent hands a PR to a security specialist. In Gastown: re-sling, history gone. In CaseHub: the original COMMAND stays in the ledger with a DELEGATED commitment pointing to the new obligor, who holds an OPEN commitment pointing back to the original. Six months later when a production incident occurs, you don't reconstruct what happened from logs — you read the obligation chain: `PR #123 → general-agent (delegated at 14:02) → security-agent (fulfilled at 16:47, findings: [...])`. Every handoff, every acknowledgement, every status update is a first-class record with a named party attached to it.

**The fleet becomes observable at scale without polling.**

At 50 agents, `list_stalled_obligations` returns the complete obligation health of the entire fleet in one query — not by checking agent health, but by querying the commitment store. You see how many obligations are being actively worked, how many are waiting for acknowledgement, how many have been delegated, how many have expired. This is work-level observability, not agent-level observability. Gastown's Witness gives you agent health. CaseHub's commitment store gives you work health. Both matter; only CaseHub has both.

**What you cannot get by adding status fields is the accountability layer itself** — the formal record of who promised what to whom, whether they followed through, and the automatic consequence of that record on future routing decisions. That is what social commitments provide. The status values are incidental. The accountability semantics are the point.

**From state machine to intelligence: why formal semantics matter for LLM reasoning.**

There is a second argument that goes beyond operational correctness. When an LLM — acting as a supervisor, an analyst, or a diagnostic agent — reads the history of what happened in a fleet, the semantic richness of what it reads directly determines the quality of reasoning it can produce.

A status field gives an LLM a label. A speech act gives an LLM a concept.

Consider two representations of the same event sequence:

*With status fields (Gastown-style):*
```
t
```

*With formal speech acts (CaseHub):*
```
14:02  COMMAND    from: casehub-devtown  to: agent-7
                  "review PR #123 for security vulnerabilities"
                  → creates OPEN commitment: agent-7 is obligated to casehub-devtown
14:05  ACKNOWLEDGED by agent-7
                  → commitment state: ACKNOWLEDGED (active, not just queued)
15:30  STATUS     from: agent-7
                  "found 3 potential injection vectors, assessing severity"
                  → assertive speech act: honest report of current state
16:47  DONE       from: agent-7
                  "PR is safe to merge, all 3 vectors assessed as false positives"
                  → commissive completion: formal assertion that obligation is fulfilled
                  → commitment state: FULFILLED → triggers LedgerAttestation
```

An LLM reading the second representation can reason, not just pattern-match. It understands that:
- COMMAND creates a *directive* — the recipient has an obligation, not just an assignment
- ACKNOWLEDGED means the agent consciously accepted the work — a gap between COMMAND and ACKNOWLEDGED indicates something
- STATUS is an *assertive* — the agent is making a truthful claim about current state under the implicit norms of the commitment
- DONE is a *commissive completion* — a formal assertion to a named party, not just a status flip

Now, six months later, a security vulnerability ships. An LLM doing post-incident analysis reads the commitment chain and can reason:

*"The STATUS at 15:30 explicitly mentioned 3 injection vectors. The DONE at 16:47 asserted they were false positives. The obligation was to review for security vulnerabilities — that obligation was formally accepted and formally declared complete. The question is not whether agent-7 reviewed the PR; they did. The question is whether their assessment of the 3 vectors was correct. The CaseContext at 16:47 shows what test results were available at that time. The trust score for agent-7 on security-sensitive work was 0.67 at the time of assignment — borderline. A higher-trust pairing might have caught this."*

Without formal semantics, the LLM reads: "bead-123 was done by agent-7 at 16:47." It cannot reason about obligation, conscious acceptance, assertive claims, or commissive completions — because those concepts don't exist in the record. It can only say "agent-7 closed the bead."

**The compounding effect: the fleet improves LLM reasoning, and LLM reasoning improves the fleet.**

When an LLM supervisor is deciding how to route the next batch of PRs, it has access to:
- Agent trust scores (derived from commitment outcomes — not from bead closure rates)
- Recent commitment histories: *"agent-7: 3 FULFILLED, 1 DECLINED (stated: outside cryptographic expertise), 0 FAILED"*
- Obligation health of the current fleet: *"12 ACKNOWLEDGED, 3 OPEN for >1hr, 1 DELEGATED awaiting specialist"*

The DECLINED carries semantic weight an LLM understands: the agent knows its limits and says so formally, rather than silently failing or producing an unreliable review. A system that distinguishes "I can't do this" (DECLINED) from "I failed trying" (FAILED) from "I handed it to someone better" (DELEGATED) gives the supervising LLM the vocabulary to reason about agent reliability, self-awareness, and capability boundaries.

The LLM reasoning: *"Agent-7 has a DECLINED on cryptographic work. The current PR touches the TLS layer. Route to agent-12 who has FULFILLED 4 consecutive security reviews with no subsequent incidents."* This is not rule-matching on a lookup table. It is reasoning over accountability records using concepts the formal semantics have made available.

Gastown's bead status does not support this reasoning. Not because it lacks the data — you could log everything Gastown does — but because the concepts needed for the reasoning (obligation, assertion, commissive act, normative completion) are not present in the record. An LLM can only reason with the concepts it has been given. Formal semantics are how you give an LLM the right concepts.

**Established methodology vs hand-rolled protocol.**

Gastown built its coordination model from first principles. The result is a well-engineered system for its domain — but it is Gastown's system, using Gastown's vocabulary, encoding Gastown's answers to questions like: how many obligation states do we need? what happens on delegation? what is the difference between a refusal and a failure? Those answers live in Gastown's documentation and Gastown's codebase.

Those same questions were answered, formally and completely, by speech act theory decades ago. The 9-type message taxonomy is not arbitrary — it is a provably complete classification of communicative acts: there is no 10th type Gastown will discover and need to add. The 7-state commitment lifecycle is not guesswork — it encodes the formally derived consequences of obligation, delegation, and failure. CaseHub inherits these answers. Gastown had to rediscover them.

The practical consequence for engineers building on each system:

| | Gastown | CaseHub |
|---|---|---|
| **When stuck on a design problem** | Search Gastown's docs or raise an issue | Search the literature — decades of answers exist |
| **Edge cases in obligation handling** | Discovered in production | Encoded in the formal model; literature covers them |
| **LLM reasoning about the system** | Draws on Gastown's documentation only | Draws on all training data about speech act theory, CMMN, Bayesian inference, deontic logic — the full body of relevant knowledge |
| **New application built on the platform** | Re-solves the same coordination problems | Inherits the same formal answers; same patterns apply |
| **Architectural consistency across apps** | Each app is hand-rolled to its domain | The methodology guides consistent solutions — the answer to "what happens when an obligation fails?" is the same in every CaseHub application |

This is the difference between a **custom protocol** and a **methodology**. Custom protocols work for their creator's use case. Methodologies guide consistent, reusable solutions across problems — and they give LLMs a shared conceptual vocabulary to reason about any system built on them, because the LLM already understands the underlying frameworks deeply from its training data.

When a new engineer joins a team using CaseHub, "what does a COMMAND mean?" has an answer outside any CaseHub document — in Searle, in deontic logic, in decades of agent systems research. When they join a Gastown team, the answer is in the Gastown glossary.

### 4.4 Trust and Reputation

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Model | Stamps (human-curated, multi-dimensional: quality, reliability, creativity) | Bayesian Beta distribution (auto-computed from attestation history, unidimensional) | Different incompleteness: Gastown lacks automation; CaseHub lacks dimensionality |
| Auto-computation | No — humans assign stamps | Yes — TrustScoreJob runs nightly | |
| Temporal decay | None | Exponential decay weighting (recent evidence counts more) | |
| Transitivity | None | EigenTrust (Kamvar et al. 2003) | |
| Sybil resistance | None — stamp collusion possible | EigenTrust eigenvector computation resists collusion | |
| Mathematical grounding | None | Beta distribution (conjugate prior for Bernoulli), EigenTrust | |
| New agent baseline | No prior = unknown | Beta(1,1) = 0.5 (uniform prior) | |
| Routing integration | No — stamps don't drive routing automatically | Yes — TrustScoreRoutingPublisher → WorkerSelectionStrategy (once P1.3 resolved) | |
| Cross-deployment federation | Yes — Wasteland stamps via DoltHub | Not yet — TrustExportService/TrustImportService planned | **Gastown advantage today** |

**CaseHub wins on model quality; Gastown wins on federation (today).** CaseHub's trust model is mathematically grounded and automatically computed. Gastown's federation via Wasteland is production-ready and CaseHub has nothing equivalent yet.

### 4.5 Audit and Accountability

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Audit mechanism | Dolt git-for-SQL history (content-addressed, Merkle Trees internally) | Merkle Mountain Range (casehub-ledger) + Doltgres git history (configurable) | Complementary when combined |
| Trust model | Admin-trusted (admin can run `dolt filter-branch` to rewrite history) | Cryptographic (inclusion proofs, no server trust required); Doltgres adds git-level audit trail on top | CaseHub + Doltgres is stronger than either alone |
| Tamper evidence | Git history — rewritable by admin but detectable via `dolt fsck` | Merkle proof (independently verifiable, Ed25519 signed checkpoints) | Doltgres rollback breaks already-signed Merkle checkpoint — detectable |
| Inclusion proofs | No | Yes — checkpoints publishable to external transparency log | |
| External verification | Impossible without server access | Any party can verify a Merkle proof independently | |
| Time-travel queries | Yes — Dolt `AS OF` syntax | **PostgreSQL (default):** No / **Doltgres backend:** Yes — same `AS OF` syntax | Gap closed by configurable backend |
| Branch-and-merge for experimental state | Yes — Dolt branches | **PostgreSQL (default):** No / **Doltgres backend:** Yes | Gap closed by configurable backend |
| Rollback | Yes — Dolt commit revert | **PostgreSQL (default):** No / **Doltgres backend:** Yes | Gap closed by configurable backend |
| GDPR Art.17 erasure with versioned history | N/A (no versioned history) | **PostgreSQL:** tombstone + Merkle chain intact ✅ / **Doltgres:** requires git history purge — harder; PII persists in old commits | **Doltgres trade-off** — regulated deployments should use PostgreSQL |
| Concurrent mutation (conflict-free) | Yes — Dolt merge resolution | Per-concern named datasources + SPI-pluggable backends | Different approaches |
| Provenance standard | None (OTel telemetry only) | W3C PROV-DM JSON-LD export | |
| Causal chain | Implicit in bead history | Explicit `causedByEntryId` on every entry | |
| GDPR Art.17 erasure | No | LedgerErasureService + ActorIdentityProvider SPI (PostgreSQL backend) | |
| GDPR Art.22 automated decision records | No | ComplianceSupplement (structured per EU AI Act Art.12) | |
| EU AI Act Art.12 | No | ComplianceSupplement + LedgerRetentionJob | |
| PII sanitisation | No | DecisionContextSanitiser SPI | |

**Mixed — and closing.** CaseHub wins on cryptographic proof and compliance. The time-travel, branching, and rollback gap is addressable via a configurable Doltgres backend (`casehub.ledger.backend=doltgres`) — Doltgres is the PostgreSQL-compatible version of Dolt, now in Beta, using the same git-for-SQL semantics. Merkle MMR and Doltgres are **complementary**, not alternatives: Doltgres adds git-level operation audit on top of cryptographic tamper-evidence, making tampering doubly detectable. The one genuine trade-off with Doltgres is GDPR Art.17 erasure — PII persists in git history even after deletion from current state. For regulated deployments (clinical, financial, legal), PostgreSQL remains the correct choice. For operational/assisteddev use cases, Doltgres closes the gap entirely.

### 4.6 Human-in-the-Loop

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Human task model | Bead assigned to human (same model as agent) | WorkItem — dedicated 10-status lifecycle | |
| SLA enforcement | None | expiresAt + claimDeadline + ExpiryCleanupJob + ClaimDeadlineJob | |
| Business hours | None | BusinessCalendar SPI | |
| Delegation | None | WorkItem DELEGATED status + EscalationPolicy | |
| Form schemas | None | formSchemaId + formPayload on WorkItem | |
| Parallel human tasks | None | WorkItemSpawnGroup with completion rollup | |
| Escalation policy | Three-tier severity (MEDIUM/HIGH/CRITICAL) with auto-re-escalation after 4 hours | EscalationPolicy SPI (pluggable per scenario) | Both support escalation; different models |
| Case integration | None — bead completion does not signal a case | casehub-work-adapter (WorkItemLifecycleEvent → PlanItem transition) — incomplete | **Neither is fully wired today** |
| Human interjection mid-case | None | Planned | |

**Winner: CaseHub** on human task semantics. The WorkItem lifecycle is purpose-built for human tasks in a way that beads are not.

### 4.7 Observability

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| OTel support | Yes — comprehensive. Per-agent `run.id` anchors all events per spawn. Tracks: `agent.instantiate`, `agent.event`, `agent.usage`, `bd.call`, `mail`, `mol.*`, `bead.create` with parent-child relationships. Opt-in verbosity control. | Yes — via Quarkus OTel extension | **Gastown advantage on OTel depth** |
| Tamper-evident audit trail | No | Merkle Mountain Range (independently verifiable) | |
| W3C PROV-DM lineage | No | LedgerProvExportService | |
| Trace correlation | Strong — `run.id` + bead hierarchy | ~~Weak — PropagationContext.traceId is UUID, OTel is W3C hex. Fixable ([engine#185](https://github.com/casehubio/engine/issues/185))~~ **✅ Fixed** — `LedgerTraceIdProvider` used at case creation | ~~**Gastown advantage today**~~ Gastown still ahead on run.id hierarchy |
| Operational tooling | `gt feed`, `gt problems`, `gt doctor`, `gt seance` | Basic claudony dashboard | **Gastown advantage** |
| Predecessor session access | `gt seance` — agents query prior session decisions | **PostgreSQL (default):** WorkerContextProvider rebuilds from ledger entries; no access to prior reasoning / **Doltgres backend:** AS OF queries enable prior-state access; reasoning traces stored in ledger become queryable at any past point | Gap addressable via Doltgres backend |

**Gastown wins on OTel depth and operational tooling today.** CaseHub wins on tamper-evident audit.

### 4.8 Agent Oversight and Recovery

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Detection | Witness timeout (per-rig), Deacon patrol (cross-rig) | `list_stalled_obligations` MCP tool, WatchdogEvaluationService | |
| Recovery | Witness re-assigns work; Deacon restarts agents; Boot validates Deacon every 5 min | Detection alerts to Qhorus channel only; no automated recovery | **Gastown advantage** |
| Recovery hierarchy | Three tiers: Witness → Deacon → Boot | One tier: WatchdogEvaluationService | **Gastown advantage** |
| Recovery action types | Re-assignment, agent restart, infrastructure validation | None yet — RecoveryPolicy SPI designed but not implemented | |
| Stale artifact cleanup | `gt stale` + dog plugins | None | **Gastown advantage** |

**Gastown wins clearly.** Hierarchical detection plus recovery is one of Gastown's strongest infrastructure capabilities. CaseHub has detection; the recovery automation is not yet built.

### 4.9 Concurrency and Scaling

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Agent spawn throttling | Scheduler — per-session API rate limit protection | Not yet built | **Gastown advantage** |
| Backpressure propagation | API rate limit handled at session level | No backpressure; SLA propagation from case budget to child items also missing | **Gastown advantage** |
| State at scale | Single Dolt server per town — potential bottleneck | Named datasources per concern + SPI-pluggable backends | CaseHub's model scales better by decomposition |
| Multi-deployment | Single town scope | Multi-deployment claudony fleet + case routing (planned) | |

**Gastown wins on concurrency control today.** CaseHub's persistence model is architecturally better for scale, but the concurrency gap is a practical blocker.

### 4.10 Extensibility

| Dimension | Gastown Foundation | CaseHub Foundation | Notes |
|-----------|-------------------|--------------------|-------|
| Extension model | Plugin system (formula overlays, role directives, 5 gate types: cooldown/cron/condition/event/manual, dog-dispatched execution, wisp-based history) | SPI-based (Java interfaces, CDI, Quarkus augmentation) | Gastown plugins are stateful and patrol-driven; CaseHub SPIs are compile-time verified |
| Worker extension | Add new polecat/crew type | Lambda / Workflow / Agent / any WorkerExecution implementation | |
| Binding extension | Plugin event gates (signal-driven dispatch) | JQ / lambda / Drools / any condition evaluator SPI | |
| Persistence extension | None (single Dolt server) | WorkItemStore SPI + any JPA-compatible backend | |
| Selection extension | None | WorkerSelectionStrategy SPI (least-loaded, semantic, trust-weighted) | |
| Notification extension | None | Connector SPI (casehub-connectors) | |
| Ecosystem | Go stdlib + ~27 dependencies (closed) | Full Quarkiverse (Kafka, Redis, gRPC, GraphQL, Micrometer, Elasticsearch, etc.) | **CaseHub advantage** |
| Compile-time verification | Runtime plugin loading | Quarkus augmentation verifies all SPI wiring at build time | **CaseHub advantage** |
| Native image | Go binary (inherently fast startup) | GraalVM native image (0.084s startup, no JVM) | |

**CaseHub wins on extensibility.** The SPI model, compile-time verification, and Quarkus ecosystem breadth are structural advantages. Gastown's plugin system is sophisticated but closed.

---

## 5. Application vs Application (Apples to Apples)

The application comparison is between Gastown's software engineering domain — the only domain it can serve — and what a CaseHub application (`casehub-devtown`) would provide for the same domain.

### 5.1 What Gastown's Application Layer Provides

Gastown's application-layer capabilities are tightly integrated with its infrastructure:

| Capability | Mechanism |
|-----------|-----------|
| Merge queue | Refinery — Bors-style batch-then-bisect, built into the daemon |
| AI coding agents | Polecats with persistent bead identity, DoltHub usernames, CV chains across ephemeral sessions |
| Human workspaces | Crew — long-lived, full git clone, distinct from agent workspaces |
| Cross-rig agent routing | `routes.jsonl` transparent bead routing across rigs |
| Workflow templates | Formulas (TOML) — reusable operational patterns, role directives, formula overlays |
| CLI | `gt` — `gt feed`, `gt problems`, `gt doctor`, `gt seance`, `gt stale`, `gt peek` — comprehensive |
| Predecessor context | `gt seance` — agents query prior sessions' decisions, not just work state |
| Federated reputation | Wasteland stamps travel across organizations via DoltHub |
| Sandboxed execution | `gt-proxy-server` with mTLS container isolation |

These capabilities are operational and battle-tested at v1.0.1. For the software engineering domain, this is a complete application.

### 5.2 What casehub-devtown Would Provide

`casehub-devtown` is a planned separate repo — not a module in `casehub-engine`. The foundation provides the primitives; the application wires them for the domain:

| Application capability | CaseHub primitive used |
|-----------------------|----------------------|
| Merge queue as a process | `CasePlanModel` — each case is a batch of MRs |
| MR human review gate | `WorkItem` with SLA + form schema + escalation policy |
| CI / lint / security check | Lambda worker or Quarkus Flow workflow worker |
| Batch-then-bisect strategy | Choreography binding: tip-of-batch fails → binding condition routes to bisect sub-case |
| Agent-to-agent review communication | qhorus typed channels (COMMAND, RESPONSE, DONE, FAILURE) |
| Failure notification | casehub-connectors Slack/Teams delivery |
| Commitment tracking per MR | qhorus 7-state Commitment lifecycle |
| Audit trail per merge decision | Merkle ledger entry with W3C PROV-DM lineage |

`casehub-devtown` does not exist yet. Gastown's Refinery is in production. This is the most significant application-layer gap.

### 5.3 What casehub-devtown Would Add That Gastown Cannot

Because `casehub-devtown` sits on CaseHub's foundation, it inherits capabilities that Gastown's application layer cannot provide — because Gastown's infrastructure does not support them:

| Capability | casehub-devtown | Gastown Refinery |
|-----------|-----------------|-----------------|
| Cryptographically tamper-evident merge decision audit | Yes — every merge decision is a Merkle entry | No — Dolt history is admin-trusted |
| Formal obligation tracking per code review assignment | Yes — qhorus COMMAND creates a Commitment | No |
| GDPR-compliant merge audit for regulated code | Yes — ComplianceSupplement + LedgerErasureService | No |
| Automatic trust score update from review outcomes | Yes — FULFILLED → LedgerAttestation → TrustScoreJob | No |
| Trust-weighted reviewer routing | Yes — TrustWeightedSelectionStrategy | No |
| Human review with SLA + business hours + delegation | Yes — WorkItem lifecycle | No (beads assigned to humans, no differentiation) |
| SLA propagation from case budget to child review tasks | Yes — SLA propagation (planned, parent#6) | No |

### 5.4 Application Domains CaseHub Enables That Gastown Cannot

Gastown's domain is fixed. Its infrastructure is the software engineering domain. CaseHub's foundation is domain-agnostic, enabling:

| Domain | Application | Foundation capabilities required |
|--------|------------|----------------------------------|
| AI coding agent coordination | casehub-devtown | All foundation primitives |
| Regulated clinical/compliance workflow *(illustrative)* | [domain app] | HITL with SLA, compliance audit, trust routing — foundation provides all primitives |
| Knowledge-intensive case management *(illustrative)* | [domain app] | Formal obligation tracking, commitment lifecycle, audit chain, GDPR |
| Regulatory decision automation *(illustrative)* | [domain app] | EU AI Act, GDPR, Merkle proofs, PII sanitisation, PROV-DM |
| Any regulated AI workflow | Domain-specific app | Compliance, cryptographic audit, trust model, formal semantics |

The pattern for all application domains: a separate repo, uses foundation primitives, adds domain logic, modifies nothing in the foundation. No Gastown code adopted; no Gastown infrastructure shared.

---

## 6. CaseHub Foundation Advantages

### 6.1 Architectural

**ACM / Blackboard paradigm.** The binding system evaluates on every `CaseContextChangedEvent`. No polling. No patrol cycles. Zero latency between a worker completing and the next scheduling decision. Multiple workers fire simultaneously if multiple bindings are satisfied by one context update. Gastown cannot do this structurally.

**Automatic parallelism.** When a worker's output satisfies multiple binding conditions simultaneously, all corresponding workers are provisioned in parallel without any declaration in a formula. Gastown requires explicit formula structure to express parallelism.

**Hybrid choreography+orchestration per case.** A case can fan out reactively across many parallel workers, then suspend at a WAITING state for a human approval decision (durable via PendingWorkRegistry), then resume reactive coordination — all within a single case, without pre-committing to one mode. Gastown is workflow-only; there is no synchronous wait primitive.

**Case-level deadlock detection.** A fixed point where no bindings fire but goals remain unsatisfied is mechanically detectable by the engine. Gastown detects only agent-level timeout.

**Binding trigger heterogeneity.** A single binding can combine: a JQ predicate over accumulated case knowledge, a CloudEvent from an external system, and a schedule. No agent reasons about this. The engine evaluates it on every state change.

**SELF_REGISTERED worker discovery.** Agents can discover running cases and offer their capabilities rather than waiting for assignment. CaseHub coordinates knowledge, not just agents.

### 6.2 Formal Semantics

| Concept | Mathematical / theoretical basis | Properties |
|---------|----------------------------------|-----------|
| Trust scoring | Bayesian Beta distribution | Optimal belief update for binary outcomes; conjugate prior for Bernoulli |
| Transitive trust | EigenTrust (Kamvar et al. 2003) | Provably sybil-resistant; collusion-resistant; converges to true reliability |
| Temporal weighting | Exponential decay | Recent evidence counts more; old evidence fades naturally |
| New agent baseline | Beta(1,1) = Uniform(0,1) | Neither trusted nor distrusted — evidence required before routing preference |
| Speech acts | Searle (1969) illocutionary taxonomy | Complete classification — all 5 categories, all possible communicative acts |
| Obligations | Von Wright deontic logic | Formal semantics for obligation, permission, prohibition |
| DELEGATED commitment transfer | Formal obligor substitution with full causedByEntryId chain | The new obligor is formally named; history is complete |

### 6.3 Accountability and Compliance

**Merkle Mountain Range.** Every ledger entry produces a Merkle inclusion proof. Ed25519-signed checkpoints can be published to an external transparency log and verified without accessing the CaseHub server. Gastown's Dolt history requires server access for any verification.

**GDPR compliance.** LedgerErasureService (Art.17 right to erasure), ComplianceSupplement (Art.22 automated decision records), DecisionContextSanitiser SPI (PII sanitisation). Gastown has no equivalent.

**EU AI Act Art.12.** ComplianceSupplement carries specific fields: `algorithmRef`, `confidenceScore`, `contestationUri`, `humanOverrideAvailable`. This is not generic logging — it is purpose-built regulatory compliance.

**W3C PROV-DM.** LedgerProvExportService provides JSON-LD lineage export. Every ledger entry carries `causedByEntryId` for explicit causal chain reconstruction.

**Three-layer actor identity.** casehub-ledger defines: persistent identity (stable trust key, persona format `{model-family}:{persona}@{major}`), configuration binding (agentConfigHash for forensic config drift detection), session correlation (ephemeral trace ID). Structured identity at a level Gastown's model does not match.

### 6.4 Extensibility

**SPI architecture.** Every major capability is behind a Java interface: WorkItemStore, WorkerSelectionStrategy, WorkerProvisioner, ConditionEvaluator, CaseChannelProvider, Connector, ActorTypeResolver, RecoveryPolicy. Any SPI can be replaced or extended without modifying the foundation.

**Compile-time verification.** Quarkus augmentation verifies all SPI wiring at build time. A misconfigured SPI fails at compile time, not at runtime under load.

**Quarkus ecosystem.** Full Quarkiverse: Kafka, Redis, MongoDB, gRPC, GraphQL, Micrometer, Elasticsearch, OIDC, JWT, WebAuthn. Gastown's dependency set is ~27 packages from Go stdlib (closed ecosystem).

**GraalVM native image.** 0.084s startup, no JVM, zero reflection overhead in production.

**Drools.** Not currently integrated — the binding system uses JQ and Java lambdas. Drools is reachable via the pluggable condition evaluator SPI without new infrastructure when needed.

**Quarkus Flow.** Integrated in the older casehub project as a worker type. The casehub-engine architecture supports it via the sealed `WorkerExecution` interface. Provides CNCF Serverless Workflow-compliant durable workflows as a peer worker type.

### 6.5 The Closed Feedback Loop

Once P0.1, P0.2, and P1.3 are resolved, CaseHub closes a loop that no existing multi-agent framework auto-closes:

```
Prescriptive (casehub-engine)  → assigns work to agent via COMMAND
Normative (casehub-qhorus)     → agent acknowledges (OPEN→ACKNOWLEDGED) and fulfills (→FULFILLED)
Evaluative (casehub-ledger)    → FULFILLED writes LedgerAttestation (SOUND) → TrustScoreJob updates Beta model
Prescriptive (casehub-engine)  → updated trust score drives next assignment via TrustWeightedSelectionStrategy
```

Self-improving without human intervention. No rules updated. No stamps assigned. Trust accumulates from cryptographically attested evidence. Gastown has no mechanism to close this loop automatically — stamps are human-curated and do not feed back into routing automatically.

---

## 7. Gastown Foundation Advantages (Honest Assessment)

These are direct infrastructure advantages — not minimised, not framed as future work. CaseHub needs to build equivalents on its own primitives.

**Operational maturity.** Gastown is v1.0.1, in production, with a known failure profile and operational tooling built from experience. CaseHub is pre-production. This is the most significant single fact.

**Hierarchical agent oversight with recovery.** Witness monitors per-rig polecats. Deacon monitors cross-rig. Boot validates Deacon every 5 minutes. Each tier detects failure and takes action — re-assignment, restart, infrastructure validation. CaseHub has detection via WatchdogEvaluationService and `list_stalled_obligations`; it has no automated recovery action. At 20+ agents, manual recovery is operationally unsustainable. Gastown solved this in production.

**Agent concurrency control.** Gastown's Scheduler prevents Claude API rate limit exhaustion at the session level. CaseHub's WorkerProvisioner spawns sessions without throttle. At 10+ concurrent cases this becomes a hard failure, not a degradation.

**Rich operational tooling.** `gt feed` (live event stream), `gt problems` (agent issue surface), `gt doctor` (system health), `gt seance` (predecessor session decision access), `gt stale` (stale artifact detection and cleanup), `gt peek` (bead inspection). CaseHub has a basic claudony dashboard and MCP tools. There is no equivalent debugging surface for multi-agent operation.

**Dolt git-for-SQL — partially closing.** Time-travel queries, branch-and-merge, rollback, and `gt seance`-style prior reasoning access are all addressable via a configurable **Doltgres** backend (`casehub.ledger.backend=doltgres`). Doltgres is the PostgreSQL-compatible version of Dolt (same DoltHub team, Beta quality 2025), using the same git-for-SQL semantics with the PostgreSQL wire protocol — Hibernate, Flyway, and JDBC all work unchanged. Doltgres and Merkle MMR are **complementary**: Doltgres adds git-level operation audit (every database commit is traceable) on top of CaseHub's cryptographic tamper-evidence, making the combination stronger than Gastown's Dolt alone. The one genuine trade-off is **GDPR Art.17 erasure** — PII persists in Doltgres git history even after deletion from current state, requiring explicit git history purging that doesn't arise with PostgreSQL. For regulated deployments, PostgreSQL remains correct. For operational/assisteddev deployments where GDPR erasure is not a constraint, Doltgres closes this gap entirely. This is a **roadmap item**, not a current capability — see Section 8.

**Single source of truth.** Everything flows through one Dolt server per town. There are no cross-repo coherence gaps of the kind CaseHub's platform audit documents (findings #1, #2, #4, #5, #7). CaseHub's distributed, SPI-pluggable persistence model is architecturally superior for extensibility and compliance, but it creates integration gaps by design. Gastown avoids them by design.

**Cross-deployment reputation.** Wasteland stamps are federated via DoltHub, production-ready, and portable across organizations. CaseHub's TrustExportService/TrustImportService is planned.

---

## 8. Foundation Roadmap — Prioritised

Ordered by when each becomes a hard blocker. Gastown hit all of these in production — that is why it built Witness/Deacon/Boot, the Scheduler, and Wasteland.

### Prerequisite Refactors — ✅ Both DONE 2026-04-29

| Prereq | Status | What shipped |
|--------|--------|-------------|
| **#67 — LedgerEntryEnricher pipeline** | ✅ DONE | `LedgerEntryEnricher` SPI, `TraceIdEnricher` extracted from `LedgerTraceListener`, non-fatal pipeline runner. ADR 0005 documenting decision. |
| **#68 — ActorTrustScore discriminator model** | ✅ DONE | `ScoreType` enum (GLOBAL/CAPABILITY/DIMENSION), `scope_key` column, UUID PK, V1001 migration rewritten. Foundation for capability-scoped and multi-dimensional scores. |

### P0 — Breaks Immediately with Multiple Agents

Wiring issues in the existing design. Not new features — completion of designed capabilities. Must be resolved before anything else has value.

#### P0.1 — Normative→prescriptive wiring ([engine#186](https://github.com/casehubio/engine/issues/186))

**Symptom:** Work is assigned to an agent but CaseHub has no way to know if the agent acknowledged its assignment or silently failed.

**Root cause:** `WorkerScheduleEvent` provisions a session and opens a Qhorus channel but never sends a COMMAND, so no Commitment is created, no obligation lifecycle runs, no trust signal is ever generated.

**Fix:** In `CaseContextChangedEventHandler` / `WorkOrchestrator.submit()`, after provisioning, call `channelProvider.postMessage(COMMAND)`. The agent's DONE/FAILURE response then drives the full normative lifecycle automatically.

**✅ Engine side DONE 2026-05-01** — `WorkerScheduleEventHandler` now dispatches a Qhorus COMMAND after scheduling (engine#186 closed). `WorkerExecutionContext` thread-local populated and `WorkerContext.channels()` available at execution time (engine#220/PR#224). Remaining: claudony-casehub `InstanceActorIdProvider` must map session IDs to persona format so trust accumulates per persona, not per ephemeral session (qhorus#124 open).

**Repos:** casehub-engine ✅, claudony-casehub (qhorus#124 pending), casehub-qhorus

#### ~~P0.2 — Commitment outcomes→trust scoring ([qhorus#123](https://github.com/casehubio/qhorus/issues/123))~~ ✅ DONE 2026-04-28

~~**Symptom:** Trust scores never update from agent behaviour. The Bayesian model has no input. Routing is permanently based on priors.~~

~~**Root cause:** `CommitmentService.fulfill()` / `.fail()` update state but never write `LedgerAttestation`. `TrustScoreJob` has no signal.~~

~~**Fix:** In `LedgerWriteService.record()`, on terminal commitment message (DONE/FAILURE/DECLINE), write a `LedgerAttestation` against the originating COMMAND entry. DONE → SOUND (confidence 0.7), FAILURE → FLAGGED (confidence 0.6), DECLINE → FLAGGED (confidence 0.4). Confidence values config-driven.~~

**Closed:** `LedgerWriteService.record()` now writes `LedgerAttestation` on DONE (SOUND, 0.7), FAILURE (FLAGGED, 0.6), DECLINE (FLAGGED, 0.4). Confidence values config-driven via `casehub.ledger.attestations.*`. 899 tests passing. Commit `17556e0`.

#### P0.3 — Actor identity fragmentation ([ledger#47](https://github.com/casehubio/ledger/issues/47), [qhorus#124](https://github.com/casehubio/qhorus/issues/124))

**Symptom:** Every new Claude session starts with zero trust, even if the same AI persona has built a strong track record. EigenTrust computes over session IDs, not personas.

**Root cause:** Qhorus `LedgerWriteService` writes `actorId = message.sender` (raw instance ID like `claudony-worker-abc123`). Persona format (`claude:analyst@v1`) never reaches the ledger from Qhorus interactions.

~~**Fix 1:** Add `ActorTypeResolver` utility to casehub-ledger — single canonical `actorId` derivation for all consumers. ([ledger#47](https://github.com/casehubio/ledger/issues/47))~~ **✅ DONE 2026-04-28** — utility created. **Consumer updates ✅ DONE 2026-04-29** — casehub-qhorus (`3cb5749`), casehub-work (`dcad49b`), claudony (`434d7df`) all now use `ActorTypeResolver.resolve()`. All four consumers complete.

~~**Fix 2:** Add `InstanceActorIdProvider` SPI to casehub-qhorus — maps Qhorus instance IDs to ledger persona IDs. claudony-casehub implements it. ([qhorus#124](https://github.com/casehubio/qhorus/issues/124)) — *pending*~~ **✅ SPI DONE 2026-04-29** — `InstanceActorIdProvider` + `DefaultInstanceActorIdProvider` (no-op identity) shipped. `CommitmentAttestationPolicy` SPI also shipped. claudony-casehub session→persona mapping implementation still pending — trust accumulation per persona not yet active (qhorus#124 open).

**Repos:** casehub-ledger, casehub-qhorus, claudony-casehub

### P1 — Breaks at Scale (10+ Concurrent Cases / Agents)

New capabilities, but hard blockers before CaseHub can operate at meaningful agent counts.

### Group A — Independent Foundational Improvements (Partial — 2026-04-29)

| Issue | Status | What shipped |
|-------|--------|-------------|
| **#55 — DecayFunction SPI + valence multiplier** | ✅ DONE | `DecayFunction` SPI extracted from `TrustScoreComputer`, `ExponentialDecayFunction` with configurable `flaggedPersistenceMultiplier`. ADR 0007. TrustScoreComputer now delegates to injected strategy. |
| **#54 — TrustGateService** | ✅ DONE | `TrustGateService` CDI bean: `meetsThreshold(actorId, minTrust)` Phase 1 shipped. Phase 2 (capability-scoped) ready for wiring once #61 completes. |
| **#53 — ActorTypeResolver consumers** | ✅ DONE | All three consumers updated (see P0.3 above). |
| **Attestation confidence** | ✅ DONE (bonus) | Bayesian Beta score now incorporates attestation confidence values, not just verdict polarity. |
| #56 — Ledger health checks | Pending | |
| #57 — Multi-attestation aggregation | Pending | |
| #58 — Compliance report API | Pending | |
| #59 — ProvenanceSupplement enricher | Pending (needs #67 ✅) | |

---

### P1 — Breaks at Scale (10+ Concurrent Cases / Agents)

#### P1.1 — Agent concurrency throttling

**Symptom:** Running 10+ cases simultaneously hits Claude API rate limits with no back-pressure. Provisioner spawns sessions without ceiling.

**Root cause:** `ClaudonyWorkerProvisioner.provision()` creates tmux sessions unconditionally.

**Fix:** Add `SpawnThrottle` to `ClaudonyConfig`:
```properties
claudony.casehub.max-concurrent-workers=20
claudony.casehub.max-workers-per-case=5
claudony.casehub.spawn-queue-timeout=PT5M
```
When ceiling reached, `provision()` queues the request rather than failing. Pure addition to `ClaudonyWorkerProvisioner`.

**Repos:** claudony (ClaudonyWorkerProvisioner, ClaudonyConfig)

#### P1.2 — Hierarchical watchdog with recovery

**Symptom:** A stuck agent requires manual intervention. At 20+ agents this is operationally unsustainable.

**Root cause:** Detection exists (WatchdogEvaluationService, `list_stalled_obligations`) but alerts only — no recovery action.

**Fix:** Add `RecoveryPolicy` SPI to casehub-engine `api/spi/`:
```java
public interface RecoveryPolicy {
    RecoveryAction decide(WorkerStalledContext ctx);
}
public enum RecoveryAction { REPROVISION, ESCALATE_TO_HUMAN, CANCEL_CASE, WAIT }
```
`WorkerStatusListener.stalled()` calls `RecoveryPolicy.decide()`. Default implementation: `ESCALATE_TO_HUMAN`. claudony-casehub provides `ReprovisioningRecoveryPolicy` that creates a new tmux session and transfers channel context.

**Repos:** casehub-engine (RecoveryPolicy SPI + default), claudony-casehub (ReprovisioningRecoveryPolicy)

#### P1.3 — Trust routing: inject WorkerSelectionStrategy + add TrustWeightedSelectionStrategy

**Symptom:** Trust scores are computed (after P0) but have zero effect on who receives work. The entire trust model is decorative without this.

**Root cause:** `CaseContextChangedEventHandler` hard-codes `LeastLoadedStrategy` rather than injecting a strategy. No trust-aware strategy exists.

**Fix:**
1. Make `WorkerSelectionStrategy` injectable in `CaseContextChangedEventHandler` (`@Inject WorkerSelectionStrategy strategy`)
2. Add `TrustWeightedSelectionStrategy` that observes `TrustScoreFullPayload` CDI events and applies trust score as a multiplier over workload count
3. `SemanticWorkerSelectionStrategy` (already in casehub-work-ai) becomes usable by casehub-engine for the first time

**Repos:** casehub-engine (CaseContextChangedEventHandler, TrustWeightedSelectionStrategy), casehub-ledger (TrustScoreRoutingPublisher already exists)

#### ~~P1.4 — Merge CaseLedgerEntry branch~~ ✅ DONE 2026-04-26

~~**Symptom:** Case lifecycle events are not in the tamper-evident ledger.~~

**Closed:** `CaseLedgerEntry`, `CaseLedgerEventCapture`, and `CaseLedgerEntryRepository` merged to main (engine#145–#148, 2026-04-26). Case lifecycle events now produce verifiable Merkle entries. `EventLog` (operational) and `CaseLedgerEntry` (compliance) co-exist. CaseHub's compliance story for regulated industries is now structurally complete.

### P1.5 — Doltgres configurable backend

**Symptom:** Time-travel queries, database branching, rollback, and prior reasoning access (`gt seance` equivalent) are Gastown advantages with no CaseHub equivalent today.

**Fix:** Add `casehub.ledger.backend` configuration property with two values:
- `postgresql` (default) — current behaviour; full Merkle MMR, GDPR Art.17 tombstone erasure, all compliance features
- `doltgres` — Doltgres PostgreSQL-compatible backend; enables time-travel (`AS OF`), branching, rollback, prior-state reasoning access; Merkle MMR stays active (complementary); GDPR Art.17 requires git history purge (unsuitable for regulated deployments)

Gate via `@IfBuildProperty("casehub.ledger.backend", "postgresql")` / `@UnlessBuildProperty` — Quarkus evaluates at build time (per `quarkus-conditional-bean-build-time-only.md` convention), giving compile-time errors rather than runtime surprises if incompatible features are combined.

**Prerequisite:** Audit all Flyway migrations across casehub-ledger, casehub-work, casehub-qhorus, casehub-engine for PostgreSQL-specific syntax incompatible with Doltgres. Doltgres does not yet support PostgreSQL extensions — verify none are used.

**Repos:** casehub-ledger (DoltgresLedgerBackend SPI), casehub-parent (backend configuration property)

### P2 — Production Quality

#### P2.1 — Cross-deployment trust federation

**Symptom:** Trust built by an agent in one CaseHub deployment is invisible to another.

**Fix:** Add to casehub-ledger: `TrustExportService` (publishes `ActorTrustScore` deltas in canonical format) and `TrustImportService` SPI (consumes trust deltas from external source, seeds Bayesian priors). Seeding Beta(α, β) from an external source rather than Beta(1,1) is a one-line change in `TrustScoreJob`. The work is the data exchange format and transport.

**Repos:** casehub-ledger

#### ~~P2.2 — OTel trace alignment ([engine#185](https://github.com/casehubio/engine/issues/185))~~ ✅ DONE 2026-04-28

~~**Symptom:** Case spans not correlatable in Jaeger/Grafana. `PropagationContext.traceId` is UUID; OTel span ID is W3C hex.~~

~~**Fix:** Populate `PropagationContext.traceId` from `LedgerTraceIdProvider.currentTraceId()` at case creation instead of `UUID.randomUUID()`.~~

**Closed:** `CaseHubReactor` now injects `LedgerTraceIdProvider` and uses `currentTraceId()` at case creation. Falls back to UUID when no active OTel span. Build clean.

#### P2.3 — Cross-repo causal chain ([claudony#94](https://github.com/casehubio/claudony/issues/94))

**Symptom:** W3C PROV-DM lineage breaks at every repo boundary.

**Fix:** `ClaudonyWorkerProvisioner.provision()` captures the active `MessageLedgerEntry.id` and passes it as `causedByEntryId` for the first `CaseLedgerEntry`. Requires `CaseLineageQuery` JPA implementation (currently `EmptyCaseLineageQuery`).

**✅ Partial progress 2026-04-29:** `CaseLineageQuery` JPA implementation shipped in claudony (`JpaCaseLineageQuery`). CaseEngine event→ledger→lineage round-trip verified end-to-end (commits `318f64b`, closes #92 and #86). `SessionRegistry.findByCaseId()` and `caseId + roleName` on `Session` also shipped — Worker↔Session correlation now stored. Remaining: passing `causedByEntryId` at provisioning time to complete the full cross-repo chain.

**Repos:** claudony-casehub, casehub-engine

### Additional completions 2026-04-29 (not P0-P2 tracked items)

| Item | Status | Notes |
|------|--------|-------|
| **casehub-work Epic #106 — Multi-instance WorkItems** | ✅ DONE | `MultiInstanceCoordinator`, `MultiInstanceGroupPolicy`, M-of-N completion, threaded inbox, claim guard, `GET /workitems/{id}/instances`. Full group policy boundary clarified in LAYERING.md. |
| **casehub-engine WorkerContextProvider + WorkerProvisioner wiring** | ✅ DONE | Wired into execution path (commit `f5a96e6`). Workers now receive lineage context at startup. |
| **claudony case worker panel (#76)** | ✅ DONE | Terminal.js case worker panel — fetch, poll, render, switch. E2E tests. Closes the Worker↔Session dashboard gap. |

### Additional completions 2026-04-30 – 2026-05-01

| Item | Status | Notes |
|------|--------|-------|
| **casehub-engine SubCase orchestration (#195)** | ✅ DONE | `SubCaseBinding`, `SubCaseExecutionHandler`, `SubCaseCompletionListener`. A case can now spawn and coordinate sub-cases with full lifecycle linkage. |
| **WorkerContext.channels() + WorkerExecutionContext thread-local (#220/PR#224)** | ✅ DONE | `WorkerContext.channels()` populated from CaseChannelProvider at execution time. `WorkerExecutionContext.current()` thread-local provides in-worker access. Enables workers to post directly to their case channels. |
| **casehub-engine DLQ replay (#194)** | ✅ DONE | Explicit dead-letter-queue replay API + auto-replay scheduler. Undeliverable events no longer silently dropped. |
| **casehub-engine idempotency window (#193)** | ✅ DONE | Configurable dedup TTL on `WorkerScheduleEventHandler` prevents duplicate provisioning on redelivered events. |
| **casehub-engine configurable worker timeout (#215)** | ✅ DONE | Worker execution timeout now configurable via `casehub.engine.worker.execution-timeout`. |
| **casehub-ledger capabilityTag on LedgerAttestation (#60)** | ✅ DONE | `capability_tag` column on `LedgerAttestation` (V1000 rewrite). Attestations now carry the capability context of the COMMAND they attest. Foundation for capability-scoped trust (ledger#61, open). |
| **casehub-qhorus capabilityTag from COMMAND content (#133)** | ✅ DONE | `LedgerWriteService` extracts `capability` from COMMAND JSON content and sets `LedgerAttestation.capabilityTag`. Attestations are now capability-scoped automatically. |
| **casehub-qhorus cross-channel obligation view (#134)** | ✅ DONE | `get_obligation_activity` MCP tool — threaded view of obligations spanning work, observe, and oversight channels. Closes the fleet-level obligation visibility gap for multi-channel deployments. |
| **casehub-work GitHub Issues sync (#157)** | ✅ DONE | WorkItem lifecycle events sync to GitHub Issue labels and state on create/claim/complete/expire. |
| **casehub-work distributed SSE via PostgreSQL LISTEN/NOTIFY (#155)** | ✅ DONE | `PostgresBroadcaster` for WorkItem queue events — SSE fan-out across multiple work-runtime instances without Redis. |
| **claudony human interjection dock (#77)** | ✅ DONE | Case worker panel with interjection dock — operators can post messages to Qhorus channels directly from the dashboard mid-case. |

### Phase-Gate Summary

| Phase | Items | Gate to next phase |
|-------|-------|-------------------|
| **P0 — Wiring** | ~~ledger#47~~ ✅ ~~qhorus#123~~ ✅ ~~qhorus#124 SPI~~ ✅ ~~engine#186 engine-side~~ ✅ · **qhorus#124 claudony persona mapping open** | Normative layer functional end-to-end; trust accumulates from real behaviour |
| **P1 — Scale** | Concurrency throttle, RecoveryPolicy SPI, trust routing wired · ~~CaseLedgerEntry merged~~ ✅ · Doltgres backend (P1.5) | Can run 10+ agents; trust actually drives routing; time-travel + branching available |
| **P2 — Quality** | ~~OTel alignment~~ ✅ · causal chain (partial ✅) · trust federation | Full observability; audit trail complete; cross-deployment trust |

---

## 9. Application Roadmap — Prioritised

Application work is distinct from foundation work. These items are built on top of the foundation and can proceed independently — but only once the relevant foundation phase-gates are met.

### A1 — casehub-devtown (AI Coding Agent Coordination Application)

`casehub-devtown` is a separate repo — not a module in casehub-engine. The foundation knows nothing about git, PRs, or CI; the application provides all domain logic.

| Application capability | CaseHub primitive | Foundation gate |
|-----------------------|------------------|----------------|
| Merge queue as a process | `CasePlanModel` per batch of MRs | P0 complete |
| MR human review gate | `WorkItem` with SLA + form schema | P1 complete |
| CI / lint / security check | Lambda or Quarkus Flow workflow worker | P0 complete |
| Batch-then-bisect strategy | Choreography binding: tip-of-batch fails → binding routes to bisect sub-case | P0 complete |
| Agent-to-agent review | qhorus typed channels (COMMAND/RESPONSE/DONE/FAILURE) | P0 complete |
| Failure notifications | casehub-connectors Slack/Teams delivery | A2 complete |
| Trust-weighted reviewer assignment | TrustWeightedSelectionStrategy | P1 complete |
| Cryptographic merge audit | Merkle ledger entry, CaseLedgerEntry | P1 complete |

This establishes the application layer pattern for any future domain applications. The pattern is always the same: separate repo, foundation primitives, domain logic, no changes to the foundation. The foundation does not know or care what domain is above it.

### A2 — Foundation Application Capabilities (Required by All Apps)

These capabilities are needed by every application domain but are implemented at the adapter/integration layer, not in the foundation itself.

**SLA propagation ([parent#6](https://github.com/casehubio/parent/issues/6)):** Case budget bounds child WorkItem and Commitment deadlines. Currently a 1-hour case can spawn a 48-hour WorkItem. Fix in `casehub-work-adapter`.

**Notification consolidation ([parent#5](https://github.com/casehubio/parent/issues/5)):** `casehub-work-notifications` Slack/Teams implementations replaced with `casehub-connectors` delegation. Unblocks unified delivery for stalled commitment alerts, case fault notifications, and escalation notifications.

**Critical event notifications:** Wire three event sources to casehub-connectors: `WatchdogEvaluationService` stall detection, `CaseLifecycleEvent(FAULTED)`, and `EscalationPolicy.escalate()` in casehub-work. Depends on notification consolidation completing first.

**Human-in-the-loop end-to-end:** Complete `casehub-work-adapter` so that `WorkItemLifecycleEvent(COMPLETED)` with a `callerRef` encoding `case:{id}/pi:{planItemId}` fires `CaseHubReactor.signal()`, transitioning the plan item from WAITING to active and triggering binding re-evaluation. Without this, CaseHub cannot orchestrate any process requiring human judgment mid-case.

### A3 — Other Planned Application Domains

Each domain application follows the same pattern established by casehub-devtown: separate repo, uses foundation primitives, adds domain logic, modifies nothing in the foundation.

The pattern the foundation enables for any future domain app: bring your domain logic, use the foundation's primitives (case engine, normative layer, trust, audit, human tasks). The foundation handles coordination, accountability, and compliance infrastructure. The domain application handles what the work actually is..

Each domain application inherits the full compliance, trust, and accountability stack from the foundation without any re-implementation. This is the strategic value of the layered architecture.

---

## 10. Internal Platform Coherence Audit

Systematic cross-capability analysis across all CaseHub repos produced 32 findings. Full audit: [casehub-parent#4](https://github.com/casehubio/parent/issues/4). All 32 findings are foundation-layer work.

Individual issues exist for the top 8:

| # | Finding | Repos | Issue | Phase |
|---|---------|-------|-------|-------|
| ~~1~~ | ~~Commitment terminal states don't write LedgerAttestation — trust scoring has no normative signal~~ **✅ DONE 2026-04-28** | qhorus, ledger | [qhorus#123](https://github.com/casehubio/qhorus/issues/123) | ~~P0.2~~ |
| ~~2~~ | ~~ActorType derivation uses 4 different logics — same actor gets different ActorType across repos.~~ **✅ DONE 2026-04-29** — `ActorTypeResolver` utility shipped; all consumers (qhorus `3cb5749`, work `dcad49b`, claudony `434d7df`, engine) updated. | ledger, work, qhorus, engine | [ledger#47](https://github.com/casehubio/ledger/issues/47) | ~~P0.3~~ |
| 3 | Two parallel delivery SPIs (casehub-connectors + casehub-work-notifications) with overlapping Slack/Teams | connectors, work | [parent#5](https://github.com/casehubio/parent/issues/5) | A2 |
| 4 | Qhorus instanceId and ledger actorId unjoined — trust doesn't accumulate across sessions of same persona | qhorus, ledger, claudony | [qhorus#124](https://github.com/casehubio/qhorus/issues/124) | P0.3 |
| 5 | Cross-repo causal chain broken — no causedByEntryId linking MessageLedgerEntry → CaseLedgerEntry → WorkItemLedgerEntry | claudony, engine, qhorus | [claudony#94](https://github.com/casehubio/claudony/issues/94) | P2.3 |
| ~~6~~ | ~~PropagationContext.traceId is UUID, OTel trace ID is W3C hex — case spans not correlatable in Jaeger~~ **✅ DONE 2026-04-28** | engine, ledger | [engine#185](https://github.com/casehubio/engine/issues/185) | ~~P2.2~~ |
| 7 | CaseHub work assignments don't create Qhorus COMMITMENTs — normative obligation lifecycle bypassed entirely. **Partial ✅ 2026-05-01** — engine side done (engine#186 closed); claudony persona→session mapping (qhorus#124) still open. | engine, qhorus, claudony | [engine#186](https://github.com/casehubio/engine/issues/186) | P0.1 |
| 8 | No SLA propagation from case budget to child WorkItems or Commitments | engine, work, qhorus | [parent#6](https://github.com/casehubio/parent/issues/6) | A2 |

Four structural themes run through all 32 findings:

| Theme | Key findings | Consequence until resolved |
|-------|-------------|---------------------------|
| **Normative↔evaluative disconnect** | #1, #7, and 10+ others | The closed feedback loop doesn't close; trust never updates from agent behaviour |
| **Actor identity fragmentation** | #2, #4 | Trust doesn't accumulate across sessions or consistently across repos |
| **Notification silo** | #3 and related | Three event sources need to notify humans; none reach human-facing channels |
| **Cross-repo causal chain broken** | #5, #6 | PROV-DM lineage complete within repos, broken at every boundary |

These are not design flaws — the prescriptive, normative, and evaluative layers are designed correctly and are not integrated. Gastown avoids this entirely because everything flows through a single Dolt server. CaseHub's distributed model is architecturally superior for extensibility and compliance but requires explicit wiring at every boundary. The wiring is incomplete; that is the completion risk.

---

## 11. Technology Stack Comparison

| Dimension | Gastown | CaseHub |
|-----------|---------|---------|
| Language | Go 1.25+ | Java 21 (on Java 26 JVM) |
| Persistence | Dolt SQL Server (git semantics: time-travel, branching, rollback) | PostgreSQL / H2 (default, Flyway managed, SPI-pluggable) · Doltgres (configurable — same git semantics as Dolt, PostgreSQL wire protocol, Beta 2025) |
| Runtime | Go binary (no runtime required) | GraalVM native image (0.084s startup) or JVM |
| Reactive model | Goroutines + patrol polling | Vert.x event loop + Mutiny reactive streams |
| Workflow | Formula (TOML) + Molecules (bead chains) | Quarkus Flow (CNCF Serverless Workflow SDK) |
| Rules / binding conditions | None (agent cognition); plugin event gates | JQ + Java lambdas (+ Drools via SPI) |
| Message protocol | Proprietary (nudge/sling) | qhorus (A2A compatible, MCP tools) |
| Observability | OTel comprehensive — per-agent run.id, agent lifecycle events, mol.* workflow stages, opt-in verbosity | OTel via Quarkus + Merkle tamper evidence + W3C PROV-DM |
| Compliance | None | GDPR Art.17/22, EU AI Act Art.12, PII sanitisation, Merkle proofs |
| Distribution | Homebrew + npm + Docker | GitHub Packages (Maven) + Docker |
| Agent support | Claude Code, Copilot, Gemini, Cursor, Codex | Claude Code (claudony), any via WorkerProvisioner SPI |
| Interface | `gt` CLI (comprehensive) | MCP tools + REST APIs |
| Extension model | Plugin system (stateful, patrol-driven, 5 gate types) | SPI-based (compile-time verified, Quarkus augmentation) |
| Ecosystem | Go stdlib ~27 deps (closed) | Full Quarkiverse (Kafka, Redis, gRPC, Elasticsearch, etc.) |
| Version | v1.0.1 (production) | Pre-production (active development) |
