# casehub-devtown — Contributor Guide

> Internal architecture, SPIs, and extension points for platform builders working on devtown internals.

**GitHub:** [casehubio/devtown](https://github.com/casehubio/devtown)

---

## PrReviewCaseDefinition

`PrReviewCaseDefinition` (promoted to `review/src/main/` in devtown#60) — fluent DSL factory with `LambdaExpressionEvaluator` for binding conditions; uses `HumanTaskTarget.inline()` for human-approval binding. `PrReviewCaseDefinitionEquivalenceTest` verifies structural parity with YAML.

## Trust-Weighted Routing Closed Loop

**`report_incident` MCP tool** (`DevtownMcpTools`): reports a production incident against a merged PR — writes FLAGGED attestation against the reviewer's trust score. Parameters: repository, prNumber, incidentId, severity, description, reviewCapability, caseId (optional). `IncidentFeedbackService` resolves merge decision from ledger, finds worker decisions, writes FLAGGED attestations with trust dimension `REVIEW_THOROUGHNESS`. `TrustFeedbackClosedLoopTest` provides E2E proof of the full chain.

## EvidentialChecker Integration

`EvidentialAttestationPolicy` (`@Alternative @Priority(2)`) consumes `EvidentialChecker` from `io.casehub.qhorus.runtime.audit`. Runs all four benchmark variants: V1 (artefact check), V2 (channel check), V3 (correlation check), V4 (token check with content). Checks run only for configured phases per capability. `EvidentialViolationStore` stores violation records. MCP tool: `get_evidential_violations` lists violations from FLAGGED attestations.

## SLA Calibration

`SlaEstimator` (`domain/sla/`): computes SLA estimate from similar past review assignments using CBR precedents (`List<Precedent>`). Returns `SlaEstimate(median, precedentCount, min, max)` — median completion time from similar cases. `SlaEstimate.toContextMap()` injects calibration data into case context (medianSeconds, precedentCount, minSeconds, maxSeconds).

## Cursor-Based Pagination

`PagedResult<T>` (`app/governance/`): generic cursor-based pagination with Base64-encoded offset cursors, configurable limit (max 200). Used by `GovernanceResource` REST endpoints: `GET /api/governance/problems`, `/reviews`, `/reviewers`, `/triage` — all accept `cursor` and `limit` query params.

## CBR PR Similarity Model

CBR Phase 1 (devtown#130, devtown#131):

- `PrFeatureVector` — structured feature extraction from PRs (file paths, modules, languages, change size, contributor)
- `WeightedJaccardSimilarity` — 5-dimension weighted scoring with per-dimension breakdown
- `CbrRetrievalService` — precedent retrieval from `CaseMemoryStore` (scan, score, rank, enrich)
- `FeatureVectorEmitter` — stores case-scoped feature vectors as memory facts at case open
- `Precedent` — past case with similarity score, feature vector, and capability outcomes
- `MemoryContext` now includes `List<Precedent> precedents` alongside existing `contributorHistory` and `codeAreaHistory`
- Uses `CaseMemoryStore` (not `CbrCaseMemoryStore`) due to four platform gaps; migration path to `CbrCaseMemoryStore` when neocortex gains `FeatureField.SetValued`

## CBR-Enhanced Reviewer Matching

CBR integrated into reviewer selection via `cbrWeight` on `TrustRoutingPolicy` (devtown#133). `DevtownTrustRoutingPolicyProvider` provides per-capability CBR weights (defaults: security-review=0.2, architecture-review=0.2, style-review=0.2). Engine-ledger's `TrustWeightedAgentStrategy` uses `AgentRoutingContext.experiences()` (populated from `RetrievedExperience` and `ExperiencePlanStep`) to apply CBR bonus. `CbrReviewerMatchingIntegrationTest` proves an agent with lower trust but higher precedent match wins over higher-trust no-precedent agent.

**CBR domain** (`domain/cbr/`): `PrFeatureVector`, `Precedent`, `WeightedJaccardSimilarity` (5-dimension weighted scoring), `SimilarityGate`, `CbrWeightAdjuster` (dynamic weight adjustment), `PrecedentActivationPolicy`, `ActivationThreshold`, `CapabilityOutcome`. Config via `CbrPreferenceKeys` (K_LIMIT, MIN_THRESHOLD, TIME_WINDOW_DAYS, weights).

**Retrieval**: `CbrRetrievalService` interface, `DefaultCbrRetrievalService` implementation (scan case-vector memories, similarity gate, score, enrich with capability outcomes, compute completion times).

## CaseMemoryStore Integration

CaseMemoryStore integration (devtown#43): contributor history, reviewer agent context, and code-area history injected before PR review case starts; review outcomes written to memory at case close.

- New domain types: `DevtownMemoryDomain`, `DevtownMemoryKeys`, `ReviewOutcome`, `ModulePathNormalizer` (devtown-domain)
- New review types: `ReviewCompletedEvent`, `MemoryContext` (devtown-review)
- New CDI components: `ReviewOutcomeObserver`, `CaseMemoryEmitter`, `CaseMemoryRecaller` (devtown-app)
- `PrPayload` enhanced with `contributor` + `changedPaths`
- Emission flow: `PlanItemCompletedEvent` -> `ReviewOutcomeObserver` -> `ReviewCompletedEvent` -> `CaseMemoryEmitter` -> `storeAll()`
- Recall: `CaseMemoryRecaller` called before `PrReviewCaseService.startCase()`
- Known tech debt: `CrossTenantCaseInstanceRepository` in async observer (engine#429 tracks fix)

## DevtownActionRiskClassifier

`DevtownActionRiskClassifier @RiskClassifier` (devtown#56): Layer 5 extension; implements engine's `ActionRiskClassifier` SPI (engine#402). 8 `DevtownActionType` constants, 4 classification categories. `DevtownRiskClassifierProducer @RiskClassifier @ApplicationScoped` CDI adapter. PreferenceProvider-driven thresholds at scope `casehubio/devtown/risk/<actionType>`. `BooleanPreference` added to domain/preferences/. `HumanOversight.GENERAL` added as catch-all oversight group. Gate operates through engine's `ActionGateWorkItemHandler` lifecycle (classifier, PendingActionGate, WorkItem, human approval, resume) — no new REST endpoints.

## Key Epics

1. Project scaffold
2. Domain model — capability tags, trust dimensions, routing thresholds
3. PR review CasePlanModel — content-driven routing and parallel checks
4. Merge queue (casehub-refinery) — batch-then-bisect
5. Cross-repo coordinated merge
6. Trust-weighted reviewer routing and post-merge feedback
7. Failure handling — DECLINED vs FAILED routing
8. GitHub integration
9. Notification wiring
10. Observability and operational tooling

Issues: https://github.com/casehubio/devtown/issues?label=epic

## Current State

Layers 1, 3, 4, 5, 6 complete; Layer 2 code complete (LAYER-LOG entry pending engine#326).

## Design Documents

| Document | What it covers |
|----------|---------------|
| `docs/gastown-casehub-analysis-v2.md` | Full architectural comparison — foundation vs foundation, application vs application |
| `docs/orchestration-advantages.md` | Seven concrete ACM advantages over workflow engines for PR review scenarios |
| `docs/DESIGN.md` | Trust-weighted selection strategy implementation detail |
