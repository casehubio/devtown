# Trust Observability — Surface Trust Model Internals to Operators

**Issue:** devtown#181
**Covers:** devtown#98 (trust visibility UI), devtown#179 (score decay investigation)
**Date:** 2026-08-04
**Branch:** issue-181-trust-observability

---

## Problem

The trust model is structurally complete — trust scores accumulate from attestations,
routing policies apply per-capability thresholds, and incident feedback degrades scores.
But operators have no visibility into any of it. The Reviewers tab shows actorId,
maturityPhase, openCommitments, and totalDecisions. The API returns trust scores in
every response, and the UI ignores them.

An operator cannot answer:
- Which agent has the highest trust for security-review?
- Why was agent-alpha chosen over agent-beta for this review?
- Is agent-gamma's trust score rising or falling?
- What threshold must an agent clear for architecture-review?
- How is decay affecting a dormant agent's scores?

## Approach

Wire the existing `blocks-trust-workbench` component (from blocks-ui) into the devtown
Reviewers tab as a split-pane. Add three backend endpoints that the trust-workbench
fetches from. Investigate decay behavior as a verification task, not a feature.

No new tables, no new migrations, no new blocks-ui components. All data comes from
existing `WorkerDecisionEntry` and `ActorTrustScore` records.

---

## Section 1: Score Proxy Endpoint

**Path:** `GET /api/governance/trust/{actorId}`

The `blocks-trust-workbench` component hardcodes `${endpoint}/trust/${actorId}` in its
internal `blocks-trust-score-panel`. With `endpoint="/api/governance"`, it fetches
`/api/governance/trust/{actorId}`. Nothing exists at that path today.

**Response shape** — must match `TrustScoreResponse` as expected by `blocks-trust-score-panel`:

```java
public record TrustScoreResponse(
    String actorId,
    Double globalScore,
    Map<String, Double> capabilityScores,
    Map<String, Double> dimensionScores) {}
```

**Implementation** — three lines of delegation in `GovernanceQueryService`:

```java
public TrustScoreResponse trustScore(String actorId) {
    return new TrustScoreResponse(
        actorId,
        trustGateService.currentScore(actorId).orElse(null),
        trustGateService.allCapabilityScores(actorId),
        trustGateService.allDimensionScores(actorId));
}
```

No new dependencies. `TrustGateService` is already injected into `GovernanceQueryService`.

---

## Section 2: Routing History Endpoints

Two endpoints serving the trust-workbench's list and detail views. The trust-workbench's
`blocks-list-pane` fetches `${endpoint}/trust/${actorId}/routing-history` for the list.
Its `_fetchDetail()` method fetches `${endpoint}/trust/${actorId}/routing-history/${decisionId}`
for detail.

### 2a: List — `GET /api/governance/trust/{actorId}/routing-history`

**Query parameters:** `limit` (default 50), `capability` (optional filter).

**Response shape:**

```java
public record RoutingHistoryList(List<RoutingHistoryEntry> decisions) {}

public record RoutingHistoryEntry(
    UUID decisionId,
    String capabilityTag,
    UUID caseId,
    Double trustScoreAtRouting,
    Double thresholdApplied,
    Instant occurredAt) {}
```

**Data source:** `WorkerDecisionEntry` is a JPA entity on the qhorus datasource.
Query via `EntityManager` with explicit type discriminator:

```sql
SELECT e FROM WorkerDecisionEntry e
WHERE e.workerId = :actorId
ORDER BY e.occurredAt DESC
```

Filter by `capabilityTag` when the `capability` query param is present. Apply limit.

**New dependency:** `GovernanceQueryService` needs the qhorus `EntityManager` injected
(or a thin repository). `WorkerDecisionEntry` is in `casehub-engine-ledger`, already
on the `app/` classpath.

### 2b: Detail — `GET /api/governance/trust/{actorId}/routing-history/{decisionId}`

**Response shape** — must match `RoutingDecisionDetail` TypeScript interface:

```java
public record RoutingDecisionDetail(
    Object rationale,
    List<GateDecisionEntry> feedback) {}

public record GateDecisionEntry(
    String verdict,
    double confidence,
    String capabilityTag,
    Instant occurredAt) {}
```

`rationale` is `Object` (serialized as raw JSON) because `WorkerDecisionEntry.routingRationale`
is already a JSON string matching the `RoutingRationaleData` TypeScript interface. Deserialize
to `JsonNode` and pass through — no Java type needed.

**Implementation:**

```java
public RoutingDecisionDetail routingDetail(String actorId, UUID decisionId) {
    // Load entry, verify actor match
    WorkerDecisionEntry wde = ...;

    Object rationale = wde.routingRationale != null
        ? objectMapper.readTree(wde.routingRationale)
        : null;

    // Attestation feedback on this entry
    List<LedgerAttestation> attestations = ledgerEntryRepository
        .findAttestationsByEntryId(decisionId, tenancyId);

    List<GateDecisionEntry> feedback = attestations.stream()
        .map(a -> new GateDecisionEntry(
            a.verdict.name(), a.confidence,
            a.capabilityTag, a.occurredAt))
        .toList();

    return new RoutingDecisionDetail(rationale, feedback);
}
```

**New dependency:** `ObjectMapper` for deserializing the rationale JSON string.
`LedgerEntryRepository` for attestation queries (in `casehub-ledger-api`, already on classpath).

---

## Section 3: Reviewers View Split-Pane

### Binding challenge

`hostPanel` passes static `panelProps` via `configure()` at mount time — no reactive
binding from table selection. The `blocks-trust-workbench` accepts `actorId` as a Lit
property but does not listen for any "reviewer selected" event — only its internal
routing events (`trust:capability-selected`, `trust-routing:selected/deselected`).

### Solution: thin wrapper component

A devtown-owned wrapper bridges table selection to the trust-workbench's `actorId`:

**File:** `app/src/main/webui/src/components/reviewer-workbench.ts`

```typescript
@customElement('devtown-reviewer-workbench')
class ReviewerWorkbench extends LitElement {
  @property() endpoint = '';
  @state() _actorId = '';

  private _unsub?: () => void;

  connectedCallback() {
    super.connectedCallback();
    this._unsub = onPagesEvent(document, 'reviewers:selected', (row) => {
      this._actorId = row.text('actorId');
    });
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this._unsub?.();
  }

  render() {
    if (!this._actorId) return html`<div class="empty">Select a reviewer</div>`;
    return html`<blocks-trust-workbench
      endpoint=${this.endpoint}
      actor-id=${this._actorId}
    ></blocks-trust-workbench>`;
  }
}
```

### Registration and layout

```typescript
// index.ts
import "@casehubio/blocks-ui-trust-workbench";
import "./components/reviewer-workbench";
registerPanel("reviewer-workbench", "devtown-reviewer-workbench");
```

```typescript
// views/reviewers.ts
export const reviewersView = page("Reviewers",
  columns([40, 60],
    [rows(
      title("Reviewer Fleet", "h2"),
      dataTable({
        lookup: lookup("reviewers", groupBy("actorId",
          col("actorId"),
          col("maturityPhase"),
          col("openCommitments"),
          col("totalDecisions")
        )),
        sortable: true,
        filter: { enabled: true },
        selectionTopic: "reviewers",
      }),
    )],
    [hostPanel("reviewer-workbench", { endpoint: "/api/governance" })],
  ),
);
```

The `selectionTopic: "reviewers"` causes the dataTable to emit `reviewers:selected`
events on row click. The wrapper listens for this event and passes `actorId` to the
inner `blocks-trust-workbench`.

**Dependency:** Verify that pages-ui's `dataTable` supports `selectionTopic`. If not,
a minor pages-ui enhancement is needed — file an issue.

### What the trust-workbench renders (self-contained)

The `blocks-trust-workbench` handles everything once it has `endpoint` and `actorId`:

- **Score panel** (top-left): global score, per-capability scores, per-dimension scores.
  Fetches `${endpoint}/trust/${actorId}`.
- **Routing history list** (bottom-left): sortable table of routing decisions with
  capability filter. Fetches `${endpoint}/trust/${actorId}/routing-history`.
- **Routing rationale detail** (right pane): candidate pool, scores, phases, selection
  reason. Fetches `${endpoint}/trust/${actorId}/routing-history/${decisionId}` on row click.
- **Feedback entries**: attestation verdicts on the selected decision.

No custom rendering in devtown. The wrapper's only job is selection binding.

---

## Section 4: Decay Investigation (#179)

### Analysis

`ExponentialDecayFunction` applies `2^(-age/halfLifeDays)` to attestation weights, with
asymmetric persistence for FLAGGED verdicts (slower decay). `TrustScoreCalculator.computeAll()`
uses these decayed weights in the Bayesian Beta computation. As attestations age, their
influence fades and the score regresses toward the Beta(1,1) prior (0.5).

Adding "score decay" on top of attestation decay would double-penalize dormant
contributors — their evidence fades via attestation decay AND their stored score decays
independently. This is incoherent with the Bayesian model where the prior already handles
uncertainty from sparse data.

### The real question

Does `TrustScoreJob` recompute scores for dormant actors (no new attestations), or only
actors with new data?

- If it recomputes all actors on each batch run → dormancy is handled automatically.
  Attestation decay causes the score to drift toward 0.5. Issue #179 closes as
  "already handled by attestation decay."
- If it skips dormant actors → `ActorTrustScore.trustScore` freezes at `lastComputedAt`.
  A contributor who was excellent 3 years ago retains that score. The fix is
  platform-level: file a casehub-ledger issue for periodic full recomputation.

### Deliverable

1. Read `TrustScoreJob` source — verify recomputation scope
2. If all actors: close #179 with rationale documenting why no new mechanism is needed
3. If only changed actors: file casehub-ledger issue for periodic full recomputation
4. No new decay mechanism in devtown regardless of finding

---

## Module Placement

All changes in `app/`. No changes to `domain/` or `review/`.

### Java

| File | Change |
|------|--------|
| `app/governance/GovernanceResource.java` | Three new endpoint methods (score proxy, routing history list, routing history detail) |
| `app/governance/GovernanceQueryService.java` | Three new query methods, `ObjectMapper` + `EntityManager` injection |
| `app/governance/GovernanceQueryService.java` | New inner records: `TrustScoreResponse`, `RoutingHistoryList`, `RoutingHistoryEntry`, `RoutingDecisionDetail`, `GateDecisionEntry` |

### Frontend

| File | Change |
|------|--------|
| `app/src/main/webui/src/components/reviewer-workbench.ts` | New — thin wrapper component |
| `app/src/main/webui/src/views/reviewers.ts` | Split-pane layout with `columns()` + `hostPanel` |
| `app/src/main/webui/src/index.ts` | Import trust-workbench, import wrapper, `registerPanel` |

No changes to `datasets.ts`. The trust-workbench fetches its own data.

### No new Flyway migrations

All data from existing `WorkerDecisionEntry` and `ActorTrustScore` tables.

---

## Tests

### Unit tests (pure Java, no Quarkus)

**`TrustScoreResponseTest`** — verify DTO assembly from mock `TrustGateService`:
- globalScore maps from `currentScore()` OptionalDouble
- Bootstrap actor produces null globalScore
- Capability and dimension maps passed through

### Integration tests (`@QuarkusTest`)

**`TrustScoreProxyEndpointTest`** — verify response shape at `/api/governance/trust/{actorId}`:
- Seed `ActorTrustScore` rows, verify JSON shape matches `TrustScoreResponse`
- Bootstrap agent returns null globalScore

**`RoutingHistoryEndpointTest`** — seed `WorkerDecisionEntry` records via `EntityManager`:
- Verify list response shape, ordering (descending by occurredAt), limit
- Verify capability filter query param
- Verify detail response with deserialized `routingRationale` and attestation feedback

### Frontend tests

Deferred — no webui testing infrastructure. Verify manually via `quarkus:dev`.

---

## Out of Scope

- **Trust score time-series** — requires a new table for periodic score snapshots.
  Routing history shows score-at-routing-time over assignments.
- **Decay simulation** — surfacing parameters via the workbench is sufficient.
  Simulation is a follow-up.
- **New decay mechanism** — this epic verifies current behavior. Policy changes are
  separate, informed by what operators observe.
- **Enhanced reviewer DTOs** — the existing `ReviewerHealth` endpoint returns
  `trustByCapability` and `trustByDimension` maps. The trust-workbench doesn't use
  that endpoint — it uses the score proxy. DTO enrichment (alpha/beta, routing policies,
  decay config) is deferred until the workbench needs it.
- **Per-case routing rationale** — `GET /api/governance/reviews/{caseId}/routing` is a
  useful cross-link but not required for the trust-workbench to function. Deferred.
- **Charts / sparklines** — the trust-workbench renders plain tables with color-coding.
  Visualization deferred.
- **Contributor trust UI** — different trust domain (merge-rate, first-attempt-quality).

---

## Done-When

1. Reviewers tab shows a split-pane: fleet table on the left, `blocks-trust-workbench`
   on the right
2. Selecting a reviewer loads their trust profile: scores by capability, scores by
   dimension, routing history list
3. Clicking a routing decision shows the full rationale: candidate pool with scores,
   phases, selection reason, and attestation feedback
4. Score proxy, routing history list, and routing history detail endpoints return
   correct JSON shapes matching the trust-workbench's TypeScript interfaces
5. Decay investigation completed: `TrustScoreJob` recomputation scope verified,
   #179 closed or platform issue filed
6. All existing tests pass plus new unit and integration tests
