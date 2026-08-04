# Contributor Trust UI Design

**Epic:** #184 (covers #182, #183)
**Branch:** issue-184-contributor-trust-ui
**Date:** 2026-08-04

## Overview

Surface the contributor trust model (devtown#24) in the devtown UI. Two deliverables:

1. **Contributor fleet view (#182)** — dedicated tab listing all contributors with trust scores, intake lanes, and dimension breakdown, with drill-down via a new contributor workbench component.
2. **Merge queue enrichment (#183)** — clicking a queued PR opens a side panel showing the PR author's trust profile (intake lane rationale, dimension scores, PR outcome history).

## Backend

### New records in GovernanceQueryService

**ContributorFleetEntry** — one row per contributor in the fleet table:

| Field | Type | Description |
|-------|------|-------------|
| actorId | String | Contributor identifier |
| trustScore | Double (nullable) | PR_CONTRIBUTION capability score; null if no history |
| intakeLane | String | FAST_TRACK / STANDARD / TRIAGE |
| observationCount | int | Total PR observations used in scoring |
| mergeRate | Double (nullable) | merge-rate dimension score |
| firstAttemptQuality | Double (nullable) | first-attempt-quality dimension score |

**ContributorDetail** — composite response for the workbench detail endpoint:

| Field | Type | Description |
|-------|------|-------------|
| actorId | String | Contributor identifier |
| globalScore | Double (nullable) | Global trust score |
| capabilityScores | Map<String, Double> | Scores per capability |
| dimensionScores | Map<String, Double> | Scores per dimension |
| intakeClassification | IntakeClassificationEntry | Lane + rationale + thresholds |
| recentOutcomes | List<PrOutcomeSummary> | Recent PR lifecycle outcomes |

**IntakeClassificationEntry:**

| Field | Type | Description |
|-------|------|-------------|
| lane | String | Assigned intake lane |
| trustScore | double | Score used for classification |
| observationCount | int | Observations used |
| classificationReason | String | Human-readable rationale |
| fastTrackThreshold | double | Current fast-track threshold |
| standardThreshold | double | Current standard threshold |

**IntakeClassificationEntry** is a new DTO in `GovernanceQueryService` that wraps the domain `IntakeClassification` record and adds policy threshold context (fastTrackThreshold, standardThreshold) from the current `ContributorIntakePolicy`. This lets the UI show where the contributor's score sits relative to the thresholds without a separate policy endpoint.

**PrOutcomeSummary:**

| Field | Type | Description |
|-------|------|-------------|
| outcome | String | SOUND or FLAGGED (ledger attestation verdict) |
| confidence | double | Attestation confidence (1.0 = certain) |
| occurredAt | Instant | When the attestation was recorded |
| trustScoreBefore | Double (nullable) | Trust score before this outcome |
| trustScoreAfter | Double (nullable) | Trust score after this outcome |

PR outcome history is sourced from **ledger attestations** — the persistent, authoritative record. `ContributorOutcomeLedgerWriter` writes attestations with `PR_CONTRIBUTION` capability when `PrLifecycleAttestationObserver` processes PR lifecycle events. These attestations are queried via JPQL on `LedgerAttestation` filtered by `actorId` and capability tag `pr-contribution`. Attestations are persistent (survive restarts) and carry the trust score delta. They do not carry repo/prNumber — the outcome list shows trust impact over time, not PR-level detail.

### Query methods

**contributorFleet():** Two-source enumeration:
1. **Trust system:** `TrustExportService.exportAll(0.0)` — actors with existing trust scores. Filter to those with a `pr-contribution` capability score.
2. **Case tracker fallback:** `PrReviewCaseTracker.activeCases()` — PR authors from active/completed cases who may not yet have trust scores (new contributors).

Union both sources by actorId (dedup). For each contributor, run `ContributorIntakePolicy.classify()` using the `PR_CONTRIBUTION` capability score (or `OptionalDouble.empty()` for new contributors) and `decisionCount()` as observations. Fetch dimension scores for `merge-rate` and `first-attempt-quality` from `TrustGateService.allDimensionScores()`.

The `ContributorIntakePolicy` thresholds come from `ContributorIntakePreferenceKeys` via `PreferenceProvider` (same source as the runtime classification).

**contributorDetail(String actorId):** Assembles the composite response. Gets trust scores from `TrustGateService` (global, capability, dimension). Runs intake classification with current policy thresholds. PR outcome history comes from **ledger attestations** queried by actorId and `pr-contribution` capability tag (see PrOutcomeSummary above). Trust score snapshots for before/after values come from `TrustScoreSnapshot` (same pattern as `TrustQueryService.loadFeedback()`).

### REST endpoints in GovernanceResource

| Method | Path | Returns |
|--------|------|---------|
| GET | `/api/governance/contributors` | `PagedResult<ContributorFleetEntry>` |
| GET | `/api/governance/contributors/{actorId}` | `ContributorDetail` |

Both endpoints follow the existing patterns: `@PermitAll`, JSON media type, cursor-based pagination on the fleet endpoint.

## blocks-ui Component

### blocks-ui-contributor-workbench

New web component in the blocks-ui repo. Custom element: `<blocks-contributor-workbench>`.

**Properties:**
- `endpoint` (string) — base API URL (e.g. `/api/governance`)
- `actorId` (string) — selected contributor ID (from `actor-id` attribute)

**Layout:** `<blocks-split-workbench>` with left summary panel and right detail pane.

**Left panel (summary):**
- Intake lane badge — colored badge (FAST_TRACK / STANDARD / TRIAGE) with classification reason as tooltip
- Trust score header — reuse `<blocks-trust-score-panel compact>` for global score + level badge
- Dimension breakdown — two score bars: merge-rate and first-attempt-quality
- Intake thresholds — visual indicator showing where the score sits relative to fast-track and standard thresholds

**Right panel (outcome history):**
- Scrollable list of recent attestation outcomes via `<blocks-list-pane>` — columns: outcome verdict badge, confidence, trust score delta (before → after), timestamp
- Display-only for now (no case linking)

**Empty/error states:**
- No actorId set → render nothing (hidden)
- Fetch error → show error message with retry affordance
- No trust data (new contributor) → show TRIAGE lane badge with "no history" message, empty outcome list

**Relationship to blocks-trust-workbench:** Separate component. Does not extend or compose `blocks-trust-workbench`. Shares `blocks-trust-score-panel` (compact mode) for score display. The trust-workbench is reviewer-oriented (routing history, gate feedback); the contributor-workbench is contributor-oriented (intake classification, attestation outcomes).

**Data fetching:** Overrides `createSourceFactory()` to bypass the pages-data extraction pipeline (per GE-20260712-7250c5). Fetches from `${endpoint}/contributors/${actorId}` and uses the raw `ContributorDetail` JSON directly.

**Events:** Emits `contributor:selected` when a new actorId loads.

**Registration in devtown:** `registerPanel("contributor-workbench", "blocks-contributor-workbench")` in `index.ts`.

## Frontend — Contributors View

### views/contributors.ts

Follows the `reviewers.ts` pattern:
- `dataTable` listing contributors from the `contributors` dataset
- Columns: actorId, trustScore, intakeLane, observationCount, mergeRate, firstAttemptQuality
- Sortable and filterable
- `hostPanel("contributor-workbench", { endpoint, actor-id: "#{row.actorId}" })` for drill-down

### Dataset binding (datasets.ts)

```
rest("contributors", "/api/governance/contributors", { dataPath: "items", refreshTime: metRefresh })
```

Added to the metrics refresh group alongside `reviewers` and `sla-comparison`.

### Tab registration (index.ts)

- Import `@casehubio/blocks-ui-contributor-workbench`
- Register panel: `registerPanel("contributor-workbench", "blocks-contributor-workbench")`
- Add tab: `["Contributors", contributorsView]` — positioned after "Reviewers" (position 5)

## Merge Queue Enrichment (#183)

Add a `hostPanel("contributor-workbench")` to the queue view (`queue.ts`) wired to the selected row's `author` field:

```
hostPanel("contributor-workbench", {
  endpoint: "/api/governance",
  "actor-id": "#{row.author}",
})
```

Clicking a queued PR shows the author's trust profile in a panel below the tables: intake lane badge with rationale, trust score with dimension breakdown, and recent PR outcome history.

No additional backend work — uses the same `GET /api/governance/contributors/{actorId}` endpoint.

**Identity mapping prerequisite:** `QueuedPrEntry.author` must use the same actor ID format as the trust system. The `PrLifecycleAttestationObserver` identifies contributors by the same ID used in `PrPayload.contributor()`. The queue entry's `author` field must match — if it uses a different format (e.g. GitHub username vs internal ID), a mapping step is needed at the queue ingestion layer, not here.

When no PR is selected the workbench renders empty (standard hostPanel behaviour).

## Known Technical Debt

**GovernanceQueryService growth:** Adding contributor fleet/detail methods continues the pattern of GovernanceQueryService accumulating query methods. The reviewer fleet and contributor fleet share structural similarities (trust export enumeration, per-actor score enrichment, maturity classification). A future refactor could extract a common `FleetQueryService<T>` or split into `ReviewerQueryService` / `ContributorQueryService`. Not in scope for this epic — the current pattern works and matches existing code.

## Data Flow Summary

```
TrustExportService.exportAll(0.0)         ContributorIntakePolicy.classify()
PrReviewCaseTracker (fallback)                        |
         |                                            v
         v                                   IntakeClassificationEntry
GovernanceQueryService.contributorFleet() ──> ContributorFleetEntry
GovernanceQueryService.contributorDetail() ──> ContributorDetail
         |                                            |
         v                                   LedgerAttestation query
GovernanceResource /api/governance/contributors[/{actorId}]
         |
         v
datasets.ts rest("contributors", ...)     blocks-contributor-workbench fetch
         |                                            |
         v                                            v
views/contributors.ts dataTable           contributor-workbench detail panel
         |                                            |
         v                                            v
"Contributors" tab                        queue.ts hostPanel on PR click
```

## Testing

- **Backend unit tests:** `ContributorFleetEntry` assembly, intake classification mapping, null/empty score handling
- **Integration test (`@QuarkusTest`):** `GET /api/governance/contributors` returns expected shape; `GET /api/governance/contributors/{actorId}` returns composite detail with classification
- **Frontend:** TypeScript type check (`npm run typecheck`), manual verification in `quarkus:dev`
- **blocks-ui:** Component builds cleanly, renders with mock data

## Dependencies

- `ContributorIntakePolicy` and `IntakeLane` from devtown-domain (already exists)
- `ContributorIntakePreferenceKeys` for threshold resolution (already exists)
- `TrustGateService` and `TrustExportService` from casehub-ledger (already injected in GovernanceQueryService)
- `blocks-ui-core`, `blocks-ui-split-workbench`, `blocks-ui-list-pane`, `blocks-ui-trust-score-panel` from blocks-ui (existing packages)
