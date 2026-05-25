# devtown Progress — Foundation Gates and Improvements

> **Protocol:** Update this document in the same session that a gate closes or an improvement ships. Never defer — drift compounds.
>
> - **Foundation Gates** — tracks which P0/P1/P2 foundation items have closed and what each unlocks for devtown. Tabular view of gaps closed.
> - **devtown Improvements** — DT-NNN entries tracking design decisions that produce capabilities Gastown structurally cannot match. Moves from Designed → Implemented as epics ship.
>
> Full architectural comparison: `docs/gastown-casehub-analysis-v2.md`

---

## Foundation Gates

Each gate is a foundation capability devtown depends on. Status shown here; full implementation detail in the linked issues/commits.

### Prerequisite Refactors

| Gate | Status | Date | Unlocks for devtown |
|------|--------|------|---------------------|
| #67 `LedgerEntryEnricher` pipeline | ✅ DONE | 2026-04-29 | Enricher SPI — OTel trace IDs flow into ledger entries; all downstream observability |
| #68 `ActorTrustScore` discriminator model | ✅ DONE | 2026-04-29 | `ScoreType.CAPABILITY` and `ScoreType.DIMENSION` with `scope_key` — per-capability and per-dimension trust scoring; foundation for DT-001, DT-003, DT-004 |

### P0 — Normative and Identity Foundation

| Gate | Status | Date | Unlocks for devtown |
|------|--------|------|---------------------|
| P0.1 engine#186 — COMMAND dispatch after worker scheduling | ✅ DONE | 2026-05-01 | Commitment lifecycle starts when work is assigned; DECLINED vs FAILED structurally captured; trust signals generated from every work outcome |
| P0.1 engine#220/PR#224 — `WorkerContextProvider` + `channels()` in-worker access | ✅ DONE | 2026-05-01 | Workers can read case context and post results; blackboard model complete |
| P0.2 qhorus#123 — commitment outcomes → `LedgerAttestation` | ✅ DONE | 2026-04-28 | DONE → SOUND, FAILURE → FLAGGED, DECLINE → FLAGGED; `TrustScoreJob` now has input; trust model active |
| P0.3 ledger#47 — `ActorTypeResolver` + all consumers updated | ✅ DONE | 2026-04-29 | Canonical `actorId` derivation across all repos; trust accumulates consistently |
| P0.3 qhorus#124 — `InstanceActorIdProvider` SPI | ✅ DONE (SPI) | 2026-04-29 | SPI contract defined; `DefaultInstanceActorIdProvider` shipped |
| P0.3 qhorus#124 — claudony persona→session mapping | ✅ DONE | 2026-05-03 | `ClaudonyInstanceActorIdProvider` (claudony#107) maps `claudony-worker-{uuid}` → `claude:{roleName}@v1`. Trust now accumulates per persona across sessions. Closed feedback loop is end-to-end. |

### P1 — Scale and Quality

| Gate | Status | Date | Unlocks for devtown |
|------|--------|------|---------------------|
| P1.1 claudony — agent concurrency throttling (`SpawnThrottle`) | ⚠️ PENDING | — | Prevents Claude API rate exhaustion at 10+ concurrent cases; hard blocker for merge queue operation |
| P1.2 casehub-engine — `RecoveryPolicy` SPI | ⚠️ PENDING | — | Automated recovery on stuck reviewer; moves from alert-only to self-healing |
| P1.3 casehub-engine — `TrustWeightedSelectionStrategy` wired | ⚠️ PENDING | — | Routing thresholds (DT-004) enforced at assignment time; without this, routing ignores trust scores |
| P1.4 casehub-engine — `CaseLedgerEntry` | ✅ DONE | 2026-04-26 | Every case lifecycle event is a tamper-evident ledger entry; merge decisions auditable |
| P1.5 casehub-ledger — Doltgres backend | ⚠️ PENDING | — | Time-travel queries; `gt seance`-equivalent predecessor context access |

### Integration Wiring

| Gate | Status | Date | Unlocks for devtown |
|------|--------|------|---------------------|
| casehub-work work#157 — GitHub Issues sync | ✅ DONE | — | WorkItems sync to GitHub Issues; human review tasks visible in standard tooling |
| parent#6 — SLA propagation case→WorkItem | ✅ DONE | 2026-05-20 | Engine-side: `HumanTaskScheduleHandler` applies `min(taskDeadline, caseBudgetDeadline)` from `PropagationContext.caseBudgetDeadline`. Claudony Commitment deadline bounding still deferred (no COMMAND-creation code yet). |
| casehub-work-adapter — HITL wiring (WorkItem COMPLETED → case signal) | ✅ DONE (happy path) | 2026-05-23 | devtown#33 wired; devtown#30 e2e test passes. Escalation-during-wait path ⚠️ still pending: `ExpiryLifecycleService` escalation doesn't signal engine (work#225, blocked on engine#349 `CaseSignalSink` SPI). |

### What devtown can build today

Based on ✅ gates: case plan models, content-driven routing bindings (Layer 5 ✅), capability vocabulary, trust dimension tracking, parallel AI checks, cryptographic audit of case decisions, GitHub Issues sync for human review tasks, Layer 2 SLA-bounded human review gate with escalation (devtown#41 ✅), HITL end-to-end happy path (devtown#33, devtown#30 ✅), trust accumulation per persona across sessions (qhorus#124 ✅).

**Blocked until P1.3 (engine#336 + engine#337 → TrustWeightedSelectionStrategy):** routing thresholds enforced at assignment. Note: qhorus#199 (`TrustGateService` never called at COMMAND creation) is an additional prerequisite — trust scores are computed but not checked when obligations open.
**Blocked until engine#349 (`CaseSignalSink` SPI):** complete escalation-during-wait signal path (work#225).
**Blocked until engine#326 (failure goal support):** SLA breach → case FAILED transition for Layer 2 complete path.

---

## devtown Improvements Over Gastown

Design decisions that produce capabilities Gastown structurally cannot match. Each entry links to the foundation gates it depends on and the epic that implements it.

---

### DT-001 — Typed vocabulary split: four types instead of a flat namespace

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** #68 ✅ (capability-scoped trust scoring); P1.3 ⚠️ (routing enforcement)

| | devtown | Gastown |
|---|---|---|
| **Vocabulary** | Four typed types: `ReviewDomain` (what the PR needs analysed), `AgentQualification` (what an agent executes with trust scoring), `HumanDecision` (formal PR accountability events), `HumanOversight` (routing confidence review) | Single flat string namespace — all work types, actor types, and orchestration operations in one tag space |
| **Removed from vocabulary** | `NOTIFY` (connector call, not trust-scored), `BATCH_BISECT`/`COORDINATED_MERGE`/`COORDINATED_ROLLBACK` (CasePlanModel structures, not capability tags — devtown#20) | All 13 original tags in one flat list |
| **Trust scoring** | Different scoring semantics per type: review domains scored on quality dimensions; executions on outcome; human decisions accumulate human trust profiles; human oversight triggers on routing uncertainty | No distinction — all capability tags routed first-come-first-served within matching tag |
| **Routing** | `ActorType`, `WorkerSelectionStrategy`, `TrustGateService`, and `RoutingPolicy` operate per vocabulary type | Capability string matching only — no actor type constraint, no trust threshold, no uncertainty handling |

**Why devtown can do this, Gastown cannot:** Gastown's flat namespace fits its flat trust model. GUPP means any available agent with the matching tag takes the work — splitting the vocabulary produces no benefit because the routing infrastructure cannot exploit the distinction. CaseHub's foundation provides `ActorType` discrimination, `ScoreType.CAPABILITY` per-capability trust scoring, and the `WorkerSelectionStrategy` SPI. The vocabulary split is the application-layer expression of capabilities the foundation already has.

---

### DT-002 — Two distinct human involvement types: decision and oversight

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** casehub-work ✅; HITL wiring ✅ (happy path devtown#33); qhorus#124 ✅ claudony#107 (trust accumulates per persona); escalation-during-wait ⚠️ (work#225, engine#349)

| | devtown | Gastown |
|---|---|---|
| **`HumanDecision`** | Formal PR accountability event — named human approves/rejects PR. `casehub-work` WorkItem lifecycle: SLA, business hours, delegation, escalation. Trust accumulates on human actors. GDPR Art.22 met structurally. | Single model: bead assigned to human same as agent — no lifecycle differentiation, no trust model for humans |
| **`HumanOversight`** | System-level review triggered when automated routing confidence is low: agent within `borderlineMargin` of threshold, no agent meets minimum threshold, fleet capability gap detected, `minimumObservations` not reached. EU AI Act Art.12 territory. | **No equivalent** — Gastown has no trust model to be uncertain about |
| **Distinction** | Two types serve two purposes: `HumanDecision` is a domain decision (is this PR safe to merge?); `HumanOversight` is a system decision (is this routing trustworthy?) | Undifferentiated — all human involvement is just "bead on human's hook" |

**Why devtown can do this, Gastown cannot:** `HumanOversight` is only possible because devtown has a trust model with a concept of uncertainty. Gastown's GUPP model has no thresholds, no observation counts, no borderline detection — there is nothing to be uncertain about. `HumanDecision`'s elevated lifecycle (SLA, escalation, trust accumulation) is made possible by `casehub-work`'s purpose-built WorkItem model, which Gastown does not have. Together, these give devtown two distinct human roles that Gastown conflates into one undifferentiated assignment.

---

### DT-003 — Trust dimensions grounded in normative layer, not duplicating capability scoring

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** P0.2 ✅; #68 ✅; qhorus#124 ✅ claudony#107 (SCOPE_CALIBRATION now accumulates per persona)

| | devtown | Gastown |
|---|---|---|
| **Dimensions** | Three: `REVIEW_THOROUGHNESS` (recall — did the agent find issues that escaped?), `FALSE_POSITIVE_RATE` (precision — did the agent flag things that weren't problems?), `SCOPE_CALIBRATION` (did the agent correctly DECLINE work outside its capability?) | Stamps: quality, reliability, creativity — human-curated, not automated |
| **Per-capability quality** | `LedgerAttestation` carries both `capabilityTag` and `trustDimension` — quality within a capability context expressed by combining fields, not a separate dimension | N/A |
| **`SCOPE_CALIBRATION`** | Maps directly to normative DECLINED commitment — agent's "I cannot do this" is a positive signal automatically captured and scored | No equivalent — cannot distinguish DECLINED from silent failure |
| **Auto-computation** | All three dimensions computed from attestation history by `TrustScoreJob` — no human curation | Stamps assigned manually |

**Why devtown can do this, Gastown cannot:** `SCOPE_CALIBRATION` is only possible because CaseHub has formal DECLINED speech acts — the agent's refusal is a first-class normative event with a named obligor, not a timeout. Gastown cannot build this dimension because it has no mechanism to distinguish refusal from failure. Per-capability quality (what `security-specialist` tried to express) is handled correctly by `ScoreType.CAPABILITY` in the ledger — a separate dimension would duplicate it. Three dimensions is the minimum meaningful set; none added until a concrete gap in routing quality justifies one.

---

### DT-004 — Routing thresholds as configurable policy, not hard-coded constants

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** P1.3 ⚠️ (engine#336 + engine#337 prerequisites open; qhorus#199 also blocks enforcement); P0.2 ✅

| | devtown | Gastown |
|---|---|---|
| **Threshold model** | `RoutingPolicy` value object — configurable per deployment, overridable per binding in case plan model | None — capability matching is first-come-first-served; no trust thresholds exist |
| **Contextual adaptation** | A binding for security review of authentication code can specify a higher threshold than one for a config change — blackboard context drives the threshold per binding | N/A |
| **Deployment configuration** | Organisations with stricter standards deploy a different `CapabilityRegistry` implementation — no code change | N/A |
| **Auditability** | Threshold applied to a routing decision recorded in EventLog as a case fact | N/A |

**Why devtown can do this, Gastown cannot:** Gastown has no trust-based routing — GUPP means work goes to whoever is available with the matching capability. devtown builds on `TrustGateService` (casehub-ledger ✅) and `WorkerSelectionStrategy` SPI (casehub-work ✅). Making thresholds configurable artifacts avoids baking intelligence into the domain model at design time — consistent with the ACM principle that routing adapts to what is known, not what was predicted.

---

### DT-005 — `RoutingPolicy`: trust-aware routing with uncertainty handling and audit rationale

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** P0.2 ✅; P1.3 ⚠️ (enforcement at assignment); ledger#76 ✅ DONE 2026-05-15 — `CAPABILITY_DIMENSION` score type with `scope_key = "{capabilityTag}:{dimensionName}"`

| | devtown | Gastown |
|---|---|---|
| **Threshold model** | `RoutingPolicy` record — `threshold`, `minimumObservations`, `borderlineMargin`, `fallbackType`, `rationale` | None — GUPP, no trust thresholds |
| **Uncertainty handling** | `borderlineMargin` triggers `HumanOversight` when agent is within margin of threshold — routing defers to human when confidence is marginal | No concept of routing uncertainty |
| **Credibility gate** | `minimumObservations` — a trust score from 2 attestations is noise; from 50 it is signal. New agents route to lower-stakes work first | No observation count concept — score of 0.9 from 1 event treated same as 0.9 from 100 |
| **Fallback** | `fallbackType` — policy-defined: escalate to `HumanOversight`, route to backup capability, hold | No fallback — whoever is available gets the work |
| **Audit** | `rationale` — why does security-review have 0.70 threshold? Captured in the policy, readable from the EventLog | No routing rationale — decisions are implicit |
| **Configurability** | Deploy a different `CapabilityRegistry` implementation for stricter standards; override per-binding in CasePlanModel | Hard-coded in formula steps |

**Why devtown can do this, Gastown cannot:** Gastown has no trust-based routing — GUPP assigns work to whoever is available with the matching tag. Routing policy is a concept that only makes sense when you have a trust model, an observation history, and a concept of routing uncertainty. devtown builds on `TrustGateService` (ledger ✅), `WorkerSelectionStrategy` SPI (casehub-work ✅), and the `HumanOversight` vocabulary type (DT-002). The policy layer connects all three into a coherent, configurable, auditable routing model that improves automatically as trust evidence accumulates.

---

### DT-006 — Trust maturity model: four phases from bootstrap to adaptive routing

**Status:** ✅ Implemented — Epic 2 (devtown#9)
**Requires:** P0.2 ✅; P1.3 ⚠️ (enforcement at assignment); ledger#76 ✅ DONE 2026-05-15 (Phase 3 `CAPABILITY_DIMENSION` quality floors now available)

| | devtown | Gastown |
|---|---|---|
| **Cold start** | Phase 0 (bootstrap): availability routing — identical to Gastown; accumulates first attestations | GUPP — permanent bootstrap; no concept of maturity |
| **Maturation** | Phases 0→1→2→3: automatic transitions as `minimumObservations` thresholds are crossed per capability per agent | No maturation — GUPP forever |
| **Degradation guarantee** | System never blocks on missing trust data — always falls back to availability routing | N/A — no trust to degrade from |
| **Phase detection** | `RoutingPolicy.isBootstrap(agentObservations)` — explicit API for routing logic to determine phase for each agent/capability pair | N/A |
| **Phase 2 quality check** | `borderlineMargin` activates at Phase 2 — human spot-checks emerge organically as data matures | N/A |
| **Phase 3 depth** | Per-capability quality floors (ledger#76) — agent thoroughness on security review separated from architecture review | N/A |

**The four phases:**

| Phase | Name | Trust data | Routing mode | `HumanOversight` trigger |
|-------|------|-----------|-------------|--------------------------|
| 0 | Bootstrap | None | Availability (Gastown parity) | Fleet gap only |
| 1 | Emerging | Sparse | Threshold for mature agents, availability for new | Fleet gap only |
| 2 | Active | Sufficient | Full threshold + borderline detection | Borderline scores + fleet gap |
| 3 | Adaptive | Rich | Threshold + per-capability quality floors | Compliance spot-checks |

**Why devtown can do this, Gastown cannot:** Gastown is permanently in Phase 0 — it has no trust model to mature. The GUPP model has no concept of "this agent now has enough history that we can trust their score." devtown starts identically to Gastown (Phase 0) and automatically improves routing as evidence accumulates. The maturity model means the architectural sophistication is always appropriate to the data that exists — no ceremony, no manual trust seeding, no configuration changes required at deployment time.

**Platform pattern:** the maturity model is not devtown-specific. Any CaseHub application using trust-based routing faces the cold-start problem. The four-phase model and `minimumObservations` gate are documented in PLATFORM.md (parent#14 closed 2026-05-21).

---

### DT-007 — Layer 2: SLA-bounded human review gate with two-tier escalation

**Status:** ✅ Implemented — devtown#41 (code), devtown#42 (wiring test)
**Requires:** casehub-work SlaBreachPolicy SPI ✅ (work#212–213); casehub-work-adapter HITL ✅ (devtown#33); engine#326 ⚠️ (failure goal — SLA breach → case FAILED transition pending)

| | devtown | Gastown |
|---|---|---|
| **First SLA breach** | `SlaBreachPolicyBean.onBreach(BreachDecision.EscalateTo)` — WorkItem `candidateGroups` rotates to `pr-leads`, status reset to PENDING for reassignment | No equivalent — bead assigned to human same as agent, no differentiation, no escalation |
| **Second SLA breach** | `SlaBreachHandler.onFail()` signals case context `{status: "sla-breach"}` via `caseHub.signal()`, triggering binding re-evaluation | No equivalent |
| **SLA policy** | `DefaultSlaBreachPolicy` — stateless, two-tier, configurable via `SlaPreferenceKey`. Displaces no-op bean at CDI level; subsequent layers can further displace | N/A |
| **Obligation lifecycle** | WorkItem carries formal SLA timer, escalation path, audit entry per state transition | Bead has a timeout but no formal obligation lifecycle |

**Why devtown can do this, Gastown cannot:** Gastown's GUPP model assigns work to humans identically to agents — bead on hook, no differentiation, no escalation chain, no formal SLA enforcement. The `SlaBreachPolicy` SPI (casehub-work#213) provides a purpose-built HITL obligation lifecycle: timers, candidateGroup rotation, and case-context signaling on terminal breach. This is possible because casehub-work treats human task completion as a first-class normative event, not just a delivery acknowledgement.

---

### DT-008 — Content-driven binding routing: specialist review fires on code content, not author labels

**Status:** ✅ Implemented — devtown#10 (Epic 3 CasePlanModel)
**Requires:** engine P0.1 ✅

| | devtown | Gastown |
|---|---|---|
| **Security review trigger** | `when: .securitySensitive == true` — fires only if code analysis placed this flag in case context; author labelling has no effect | Formula step with role directive — author or operator declares whether security review is needed |
| **Automatic parallelism** | Multiple checks (style, test coverage, performance) fire simultaneously when their binding conditions are satisfied by the same context update — no explicit parallel declaration | Explicit formula structure required to express parallelism |
| **Human + CI in parallel** | Human approval and CI check enter WAITING state simultaneously — total wall time = max(approval, CI), not sum | Sequential by default; explicit convoy structure to express overlap |
| **Routing audit** | Every binding dispatch recorded in EventLog as a case fact — why security review ran (or didn't) is queryable | No routing rationale captured |

**Why devtown can do this, Gastown cannot:** Gastown uses formula steps (TOML) with explicit sequence and role directives. Content-driven conditional dispatch — route to specialist only if the content warrants it — requires a rule evaluation engine over accumulated blackboard state. CaseHub's binding system evaluates JQ predicates over CaseContext on every `CONTEXT_CHANGED` event: content analysis runs first, specialist review fires only if analysis finds security-sensitive code. Gastown has no equivalent to predicate-gated, content-reactive routing. **Note:** as of engine#335, the `when:` field is evaluated via `contextChange.filter` at the engine level — the application-level unit tests confirm correctness independently.

---

### DT-009 — Ed25519 bilateral agent signing: non-repudiation beyond admin trust

**Status:** ✅ Implemented (foundation — inherited by all domain apps) — ledger#79, #80, #83, #84
**Requires:** casehub-ledger ✅

| | devtown (via foundation) | Gastown |
|---|---|---|
| **Platform signing** | Ed25519-signed COMMAND — agent cannot claim it never received an instruction | Dolt history is admin-trusted; a database admin can rewrite history |
| **Agent signing** | Ed25519-signed RESPONSE — platform cannot deny what the agent returned | No equivalent |
| **Bilateral** | Both sides sign; neither can unilaterally repudiate | N/A |
| **Compromise detection** | Signature verification failure fires CDI event for real-time alerting | N/A |
| **Key rotation** | Key versioning with rotation history; each entry carries `signingKeyVersion` | N/A |
| **Post-quantum path** | Algorithm-transparent SPI; ADR documenting PQC migration path (ledger#84) | N/A |
| **Third-party verification** | Ed25519-signed Merkle checkpoints publishable to external transparency log; verifiable without server access | Verification requires server access |

**Why devtown can do this, Gastown cannot:** Gastown's tamper-evidence model stops at Dolt's git-level audit — the database admin is the trusted party. CaseHub's bilateral Ed25519 signing means: (a) agents cannot dispute having received a COMMAND or issued a RESPONSE, (b) Merkle inclusion proofs are verifiable by any third party without server access, (c) signing key compromise is detected and alerted in real time. This capability is inherited automatically by all domain applications including devtown without any application-layer implementation.

---

### DT-010 — Trust federation: cross-deployment reputation via ledger export/import

**Status:** ✅ Implemented (foundation — inherited by all domain apps) — ledger#63, #64, #65 (shipped 2026-05-14)
**Requires:** casehub-ledger ✅

| | devtown (via foundation) | Gastown |
|---|---|---|
| **Export** | `TrustExportService` — publishes `ActorTrustScore` deltas in canonical format, endpoint behind config flag | Wasteland stamps travel across organizations via DoltHub — production-ready |
| **Import** | `TrustImportService` SPI — consumes trust deltas from external deployments, seeds Beta(α,β) priors | N/A |
| **Trust basis** | Cryptographically derived from attested outcomes (DONE → SOUND, FAILURE/DECLINE → FLAGGED) — imported trust carries mathematical guarantees of Bayesian Beta model | Human-curated stamps — not automatically derived from attested outcomes |
| **Cold start** | New agent: Beta(1,1) prior; with imported trust: Beta(α,β) seeded from external history — bootstraps immediately | No equivalent bootstrapping mechanism |

**Why devtown can do this, Gastown cannot:** Gastown's Wasteland is more mature (production-ready, portable). CaseHub now has structural parity with `TrustExportService` + `TrustImportService` (P2.1 complete). The architectural difference: Gastown's stamps are human-curated — a trusted person assigns them. CaseHub's exported trust is automatically derived from cryptographically attested commitment outcomes. Imported trust carries the same mathematical provenance as locally-computed trust, making cross-deployment reputation structurally more reliable than stamp-based models.
