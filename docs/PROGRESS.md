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
| P0.3 qhorus#124 — claudony persona→session mapping | ⚠️ PENDING | — | Trust accumulates per persona across sessions, not per ephemeral session; required before routing reflects long-term trust |

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
| parent#6 — SLA propagation case→WorkItem | ⚠️ PENDING | — | Case-level deadlines flow into human review WorkItems; SLA enforcement end-to-end |
| casehub-work-adapter — HITL wiring (WorkItem COMPLETED → case signal) | ⚠️ PENDING | — | Human review completion unblocks case progression; end-to-end human-in-the-loop |

### What devtown can build today

Based on ✅ gates: case plan models, content-driven routing bindings, capability vocabulary, trust dimension tracking, parallel AI checks, cryptographic audit of case decisions, GitHub Issues sync for human review tasks.

**Blocked until qhorus#124:** trust routing that reflects long-term agent reputation (scores accumulate per session only).
**Blocked until P1.3:** routing thresholds enforced at assignment (design complete; enforcement not yet wired).
**Blocked until HITL wiring:** full human-in-the-loop PR approval cycles.

---

## devtown Improvements Over Gastown

Design decisions that produce capabilities Gastown structurally cannot match. Each entry links to the foundation gates it depends on and the epic that implements it.

---

### DT-001 — Typed vocabulary split: three types instead of a flat namespace

**Status:** Designed — Epic 2 (devtown#9)
**Requires:** #68 ✅ (capability-scoped trust scoring); P1.3 ⚠️ (routing enforcement)

| | devtown | Gastown |
|---|---|---|
| **Vocabulary** | Three typed types: `ReviewDomain` (what the PR needs analysed), `AgentQualification` (what an agent is certified to execute), `HumanDecision` (formal accountability events) | Single flat string namespace — all work types, actor types, and orchestration operations in one tag space |
| **Trust scoring** | Different scoring semantics per type: review domains scored on quality dimensions; executions on outcome; human decisions on human trust profiles | No distinction — all capability tags routed first-come-first-served within matching tag |
| **Routing** | `ActorType`, `WorkerSelectionStrategy`, and `TrustGateService` operate per vocabulary type | Capability string matching only — no actor type constraint, no trust threshold |

**Why devtown can do this, Gastown cannot:** Gastown's flat namespace fits its flat trust model. GUPP means any available agent with the matching tag takes the work — splitting the vocabulary produces no benefit because the routing infrastructure cannot exploit the distinction. CaseHub's foundation provides `ActorType` discrimination, `ScoreType.CAPABILITY` per-capability trust scoring, and the `WorkerSelectionStrategy` SPI. The vocabulary split is the application-layer expression of capabilities the foundation already has.

---

### DT-002 — Human review as formal accountability event, not routing constraint

**Status:** Designed — Epic 2 (devtown#9)
**Requires:** casehub-work ✅; HITL wiring ⚠️ (for end-to-end); qhorus#124 ⚠️ (for trust to accumulate per human persona)

| | devtown | Gastown |
|---|---|---|
| **Model** | `HumanDecision` — first-class vocabulary type with dedicated routing mechanics, `ActorType.HUMAN` enforcement, and `casehub-work` lifecycle | `human-approval-gate` or equivalent — capability tag like any other; beads assigned to humans same as agents |
| **Lifecycle** | `casehub-work` WorkItem: 10-status lifecycle, SLA, business hours (`BusinessCalendar` SPI), delegation, escalation, form schema | None — no differentiation between human and agent work lifecycle |
| **Trust** | Human actors accumulate trust scores from review outcomes; senior reviewers who catch what agents miss earn higher routing priority | No trust model for humans — stamps human-curated, not outcome-derived |
| **Compliance** | GDPR Art.22 oversight requirement met structurally — named human is formally accountable for the decision record | None |

**Why devtown can do this, Gastown cannot:** Gastown assigns beads to humans identically to polecats — a bead marked DONE by a human carries the same record as one marked DONE by an agent. `casehub-work`'s dedicated WorkItem lifecycle is purpose-built for human task semantics. The trust model runs identically for human and agent actors — the same `LedgerAttestation` mechanism records the outcome and feeds `TrustScoreJob`. Naming it a "gate" diminishes what is actually a formal accountability event; the vocabulary reflects its weight.

---

### DT-003 — Trust dimensions grounded in normative layer, not duplicating capability scoring

**Status:** Designed — Epic 2 (devtown#9)
**Requires:** P0.2 ✅; #68 ✅; qhorus#124 ⚠️ (for SCOPE_CALIBRATION to accumulate per persona)

| | devtown | Gastown |
|---|---|---|
| **Dimensions** | Three: `REVIEW_THOROUGHNESS` (recall — did the agent find issues that escaped?), `FALSE_POSITIVE_RATE` (precision — did the agent flag things that weren't problems?), `SCOPE_CALIBRATION` (did the agent correctly DECLINE work outside its capability?) | Stamps: quality, reliability, creativity — human-curated, not automated |
| **Per-capability quality** | `LedgerAttestation` carries both `capabilityTag` and `trustDimension` — quality within a capability context expressed by combining fields, not a separate dimension | N/A |
| **`SCOPE_CALIBRATION`** | Maps directly to normative DECLINED commitment — agent's "I cannot do this" is a positive signal automatically captured and scored | No equivalent — cannot distinguish DECLINED from silent failure |
| **Auto-computation** | All three dimensions computed from attestation history by `TrustScoreJob` — no human curation | Stamps assigned manually |

**Why devtown can do this, Gastown cannot:** `SCOPE_CALIBRATION` is only possible because CaseHub has formal DECLINED speech acts — the agent's refusal is a first-class normative event with a named obligor, not a timeout. Gastown cannot build this dimension because it has no mechanism to distinguish refusal from failure. Per-capability quality (what `security-specialist` tried to express) is handled correctly by `ScoreType.CAPABILITY` in the ledger — a separate dimension would duplicate it. Three dimensions is the minimum meaningful set; none added until a concrete gap in routing quality justifies one.

---

### DT-004 — Routing thresholds as configurable policy, not hard-coded constants

**Status:** Designed — Epic 2 (devtown#9)
**Requires:** P1.3 ⚠️ (for thresholds to be enforced at assignment); P0.2 ✅ (for trust scores to exist)

| | devtown | Gastown |
|---|---|---|
| **Threshold model** | `RoutingPolicy` value object — configurable per deployment, overridable per binding in case plan model | None — capability matching is first-come-first-served; no trust thresholds exist |
| **Contextual adaptation** | A binding for security review of authentication code can specify a higher threshold than one for a config change — blackboard context drives the threshold per binding | N/A |
| **Deployment configuration** | Organisations with stricter standards deploy a different `CapabilityRegistry` implementation — no code change | N/A |
| **Auditability** | Threshold applied to a routing decision recorded in EventLog as a case fact | N/A |

**Why devtown can do this, Gastown cannot:** Gastown has no trust-based routing — GUPP means work goes to whoever is available with the matching capability. devtown builds on `TrustGateService` (casehub-ledger ✅) and `WorkerSelectionStrategy` SPI (casehub-work ✅). Making thresholds configurable artifacts avoids baking intelligence into the domain model at design time — consistent with the ACM principle that routing adapts to what is known, not what was predicted.
