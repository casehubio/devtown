# Squash Plan — upstream/main..HEAD (2026-05-23)

50 commits → 18 commits. 32 absorbed, 0 dropped.
Working branch: squash/wip-main-20260523-014850

---

## Group 1 — Epic 2 baseline
*2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `8b3305e` refactor(domain): promote CapabilityRegistry.isKnown to SPI default method | ✅ KEEP | *(message adequate — unchanged)* |
| `e35f267` build(deps): bump assertj-core from 3.24.2 to 3.27.7 | 🔽 SQUASH ↑ | *(absorbed — small dep bump, same-session, no issue ref)* |

> **Result:** 1 commit.

---

## Group 2 — CLAUDE.md initial setup
*3 commits → 1*
**Final message:** `docs(claude): add Git Discipline section, design doc convention, and artifact routing`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `e1f4139` docs(claude): add Git Discipline section — workspace/project repo split | ✅ KEEP | *(see Final message above)* |
| `da9b4cd` docs(claude): fix artifact routing — adr/blog/specs → project, design/snapshots/handover → workspace | 🔽 SQUASH ↑ | *(absorbed — routing fixup, same CLAUDE.md concern)* |
| `4b19bcb` docs(claude): add design doc convention + complete workflow chain | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md addendum, same session)* |

> **Result:** 1 commit.

---

## Group 3 — Contributor trust proposal
*1 commit → 1 (already standalone)*

✅ KEEP `1773673` docs: add contributor trust TL;DR proposal (devtown#24)

> **Result:** 1 commit.

---

## Group 4 — Agentic harness docs
*2 commits → 1*
**Final message:** `docs: add agentic harness goals, LAYER-LOG.md obligation, reference docs, and portable paths`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `2a8bc7f` docs: add agentic harness goals, LAYER-LOG.md obligation, and reference docs | ✅ KEEP | *(see Final message above)* |
| `99f425b` docs: replace machine-specific paths with relative peer paths | 🔽 SQUASH ↑ | *(absorbed — stale-ref fixup on same docs; 2a8bc7f introduced the paths that needed fixing)* |

> **Result:** 1 commit.

---

## Group 5 — Design spec promotions
*1 commit → 1 (standalone)*

✅ KEEP `3747999` docs: promote Epic 1 and Epic 2 design specs to project repo

> **Result:** 1 commit.

---

## Group 6 — LAYER-LOG setup
*5 commits → 1*
**Final message:** `docs: add retroactive LAYER-LOG.md — Layers 1–7 with protocol fixes and CLAUDE.md routing alignment`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3777716` docs: add retroactive LAYER-LOG.md — Layers 1-7 with placeholders | ✅ KEEP | *(see Final message above)* |
| `7491420` docs(claude): clarify LAYER-LOG.md — epics≠layers, incremental entries | 🔽 SQUASH ↑ | *(absorbed — immediate clarification of same commit)* |
| `cb3ca2c` docs: remove unstarted layer skeleton entries from LAYER-LOG.md | 🔽 SQUASH ↑ | *(absorbed — cleanup of same LAYER-LOG commit)* |
| `2268d87` docs: apply four protocol fixes to LAYER-LOG.md | 🔽 SQUASH ↑ | *(absorbed — protocol fixups on same document)* |
| `4a5fa6b` fix(claude): standardise routing table — adr/specs→project, blog→workspace→mdproctor.github.io | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md routing stabilisation, same session)* |

> **Result:** 1 commit.

---

## Group 7 — Layer 1 Part B: naive service
*4 commits → 1*
**Final message:** `feat(app): Layer 1 Part B — naive PR review service, gap comments, LAYER-LOG entry`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cca6acc` feat(app): Layer 1 Part B — naive PR review service with gap comments | ✅ KEEP | *(see Final message above)* |
| `1c6921f` docs(specs): Layer 1 Part B naive service design spec | 🔽 SQUASH ↑ | *(absorbed — pre-implementation spec for this feat)* |
| `1fa95ce` docs(layer-log): complete Layer 1 Part B entry; add Layer 5 stub | 🔽 SQUASH ↑ | *(absorbed — LAYER-LOG entry for this exact layer)* |
| `18b22e0` refactor(review): move PrReviewApplicationService and DTOs to review module | 🔀 MERGE ↑ | *(merged — same Layer 1 Part B scope; SPI moved from app to review as part of the same structural decision)* |

> **Result:** 1 commit.

---

## Group 8 — PrReviewResource dispatcher
*1 commit → 1 (standalone)*

✅ KEEP `2944072` feat(app): PrReviewResource — POST /api/reviews dispatcher

> **Result:** 1 commit.

---

## Group 9 — PR review CasePlanModel YAML (Epic 3 core)
*3 commits → 1*
**Final message:** `feat(review): PR review CasePlanModel YAML — 9 bindings, 3 goals, casehub-engine dep`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `d8a70d9` feat(review): PR review CasePlanModel YAML — 9 bindings, 3 goals | ✅ KEEP | *(see Final message above)* |
| `a5e21bf` docs(specs): Epic 3 PR review CasePlanModel design spec | 🔽 SQUASH ↑ | *(absorbed — pre-implementation spec)* |
| `ae55054` build(app): add casehub-engine runtime dep for YamlCaseHub | 🔽 SQUASH ↑ | *(absorbed — build dep required by this feat; < 5 lines)* |

> **Result:** 1 commit.

---

## Group 10 — PrReviewCaseHub service (Epic 3 wiring)
*5 commits → 1*
**Final message:** `feat(app): PrReviewCaseHub and PrReviewCaseService — displaces naive impl`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `85efe5c` feat(app): PrReviewCaseHub and PrReviewCaseService — displaces naive impl | ✅ KEEP | *(message adequate — unchanged)* |
| `445d6fd` fix(app): remove duplicate pr-review.yaml from app resources | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, cleanup artifact from this feat)* |
| `36cd91b` fix(app): quality fixes from code review — document startCase discard, implement findLatestBySubjectId | 🔽 SQUASH ↑ | *(absorbed — post-review fixups on this feat)* |
| `b65ccb9` test(app): YAML round-trip @QuarkusTest for PrReviewCaseHub | 🔽 SQUASH ↑ | *(absorbed — integration test for this exact class)* |
| `d9499d7` docs(layer-log): complete Layer 5 entry — Epic 3 shipped | 🔽 SQUASH ↑ | *(absorbed — LAYER-LOG entry for this layer)* |

> **Result:** 1 commit.

---

## Group 11 — Binding condition unit tests (TDD)
*3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `098fc92` test(review): 27 binding condition unit tests — TDD, pure unit, no Quarkus | ✅ KEEP | *(message adequate — unchanged)* |
| `81cfdc4` fix(review): merge binding missing securitySensitive conditional | 🔽 SQUASH ↑ | *(absorbed — test-driven fix for this test suite)* |
| `a7003b4` fix(review): pr-approved goal missing securitySensitive conditional | 🔽 SQUASH ↑ | *(absorbed — same; both are corrections discovered by 098fc92 tests)* |

> **Result:** 1 commit.

---

## Group 12 — CLAUDE.md / layer-log docs + contrib trust spec (Epic 3 close)
*3 commits → 1 (SQUASH into nearest KEEP)*
⚠️ **Proximity-grouped** — these docs commits are absorbed into d8a70d9 as the nearest meaningful KEEP for the Epic 3 close period.

| Commit | Action | Curated result |
|--------|--------|----------------|
| `b79fec6` docs(claude): add Epic 3 and parent#26 to foundation gates | 🔽 SQUASH ↑ `d8a70d9` | *(absorbed — CLAUDE.md update recording Epic 3 complete)* |
| `0f70697` docs(specs): promote contributor trust open source spec | 🔽 SQUASH ↑ `d8a70d9` | *(absorbed — spec file copy, < 5 lines effective)* |

---

## Group 13 — PrFinding and PrVerdict (Layer 1 API stability)
*2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `47e2266` feat(review): introduce PrFinding and PrVerdict for Layer 1 API stability | ✅ KEEP | *(message adequate — unchanged)* |
| `d0bfdfa` test(review): add missing doesNotFire_whenAnalysisNotComplete tests and use method references | 🔽 SQUASH ↑ | *(absorbed — test additions for the same feat; < 5 lines effective change)* |

> **Result:** 1 commit.

---

## Group 14 — NaivePrReviewService fixture + PrReviewResource integration test
*1 commit → 1 (standalone)*

✅ KEEP `f687e5f` test(app): extract NaivePrReviewService fixture and add PrReviewResource integration test

> **Result:** 1 commit.

---

## Group 15 — HITL work-adapter wiring (devtown#33)
*5 commits → 1*
**Final message:** `feat: wire casehub-engine-work-adapter for HITL case resumption (Closes #33)`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `e4830ea` feat: wire casehub-engine-work-adapter for HITL case resumption | ✅ KEEP | *(see Final message above)* |
| `448e05b` chore: add DESIGN.md stub — Refs casehubio/parent#31 | 🔽 SQUASH ↑ | *(absorbed — tiny chore, < 5 lines)* |
| `e4e2a86` docs: clarify selected-alternatives build-time semantics (engine#293) | 🔽 SQUASH ↑ | *(absorbed — docs clarification triggered by this wiring work)* |
| `ec610d3` docs(CLAUDE.md): mark HITL wiring complete — devtown#33 done, devtown#30 unblocked | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md status update for this feat)* |
| `a0f0fc7` feat: wire casehub-engine-work-adapter for HITL case resumption (closes #33) | 🔽 SQUASH (merge commit) | *(local topic merge — consolidates 448e05b + ec610d3; content already captured in absorbed commits)* |

> **Result:** 1 commit.

---

## Group 16 — HITL e2e integration test (devtown#30)
*3 commits → 1*
**Final message:** `test(app): HITL end-to-end integration test for human approval binding (Closes #30)`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3271d8f` test(app): HITL end-to-end integration test for human approval binding (devtown#30) | ✅ KEEP | *(see Final message above)* |
| `067da9f` protocol(PP-20260521-134c38, PP-20260521-a36692): HITL test context pre-seeding and MemoryPlanItemStore selection | 🔽 SQUASH ↑ | *(absorbed — protocol doc commit accompanying this test)* |
| `a44190a` feat(docs): promote HITL human approval lifecycle spec from issue-30-hitl-human-approval-test | 🔽 SQUASH ↑ | *(absorbed — spec file copy tied to this test work)* |

> **Result:** 1 commit.

---

## Group 17 — Protocol path refactor + CLAUDE.md/LAYER-LOG alignment
*2 commits → SQUASH into Group 16*
⚠️ **Proximity-grouped** — no standalone feature; absorbed into nearest KEEP.

| Commit | Action | Curated result |
|--------|--------|----------------|
| `e7c9a47` refactor(docs): update protocol paths to casehub/garden | 🔽 SQUASH ↑ `3271d8f` | *(absorbed — stale-ref fixup on protocol paths)* |
| `84dce53` docs: align CLAUDE.md and LAYER-LOG.md with AML tutorial-layer discipline | 🔽 SQUASH ↑ `f53899a` | *(absorbed — CLAUDE.md/LAYER-LOG alignment, nearest KEEP is Layer 2 spec)* |

---

## Group 18 — Layer 2 SLA spec + CDI fix (issue-38)
*4 commits → 2*

### 18a — Layer 2 SLA breach policy spec
**Final message:** `docs(specs): Layer 2 SLA breach policy design — protocols and CLAUDE.md update (Refs #38)`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `f53899a` docs(specs): Layer 2 SLA breach policy design — devtown#38 | ✅ KEEP | *(see Final message above)* |
| `c52d308` protocol(PP-20260522-fe93b6, PP-20260522-f08b62): cross-repo state verification + transactional loop exception safety | 🔽 SQUASH ↑ | *(absorbed — protocol docs accompanying the Layer 2 design work)* |
| `1b13586` docs(CLAUDE.md): flag Path.root() pending + transactional loop protocol ref | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md update tied to Layer 2 design)* |

### 18b — CDI deployment fix
**Final message:** `fix(build): resolve non-persistence CDI deployment problems (Closes #31, Refs #39)`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3e4f22f` fix(build): resolve non-persistence CDI deployment problems (Closes #31) | ✅ KEEP | *(see Final message above)* |
| `19543c4` chore: add casehub-platform-api compile dep and casehub-platform-testing test dep to devtown-app (devtown#39) | 🔽 SQUASH ↑ | *(absorbed — pre-existing gap fix that enabled the CDI work; Refs #39 added to curated message)* |

> **Result:** 2 commits.

---

## AFTER — what `git log --oneline` will show (estimated)

```
  50  commits (original)
  -32  absorbed by squash
  ──────────────────────────────────────────
  18  commits — no content lost

Estimated sample (most recent → oldest):
  fix(build): resolve non-persistence CDI deployment problems (Closes #31, Refs #39)
  docs(specs): Layer 2 SLA breach policy design — protocols and CLAUDE.md update (Refs #38)
  test(app): HITL end-to-end integration test for human approval binding (Closes #30)
  feat: wire casehub-engine-work-adapter for HITL case resumption (Closes #33)
  test(app): extract NaivePrReviewService fixture and add PrReviewResource integration test
  feat(review): introduce PrFinding and PrVerdict for Layer 1 API stability
  test(review): 27 binding condition unit tests — TDD, pure unit, no Quarkus
  feat(app): PrReviewCaseHub and PrReviewCaseService — displaces naive impl
  feat(review): PR review CasePlanModel YAML — 9 bindings, 3 goals, casehub-engine dep
  feat(app): PrReviewResource — POST /api/reviews dispatcher
  feat(app): Layer 1 Part B — naive PR review service, gap comments, LAYER-LOG entry
  docs: add retroactive LAYER-LOG.md — Layers 1–7 with protocol fixes and CLAUDE.md routing alignment
  docs: promote Epic 1 and Epic 2 design specs to project repo
  docs: add agentic harness goals, LAYER-LOG.md obligation, reference docs, and portable paths
  docs: add contributor trust TL;DR proposal (devtown#24)
  docs(claude): add Git Discipline section, design doc convention, and artifact routing
  refactor(domain): promote CapabilityRegistry.isKnown to SPI default method
```
