# Per-Capability Duration Breakdown in SLA Calibration

**Issue:** casehubio/devtown#152
**Parent:** #136 (SLA calibration)
**Date:** 2026-08-01

## Problem

SLA calibration currently computes only total case duration statistics (median/min/max)
from historical precedents. There is no visibility into how long individual capabilities
(code-analysis, security-review, etc.) take. Per-capability timing is needed for granular
SLA visibility and future per-capability SLA overrides.

## Data Available

Each memory fact in `DefaultCbrRetrievalService.enrichOutcomes()` already carries:
- `capability` attribute (e.g. "code-analysis", "security-review")
- `createdAt` timestamp (when the capability completed)
- Case start time (`cv.startedAt()`) is known from the candidate vector

Per-capability duration = `fact.createdAt - caseStartedAt` (time-to-completion from case
start). This data flows through `enrichOutcomes()` today but is discarded — only the latest
timestamp across all capabilities is kept for total case duration.

**Known bias:** `cv.startedAt()` is the feature-vector memory fact's `createdAt`, which is
written by `featureVectorEmitter.emit()` — slightly after `caseHub.startCase()`. All
per-capability durations are systematically underestimated by this millisecond-scale delay.
This is acceptable for SLA calibration (order-of-minutes precision); exact case start
timestamps would require querying the case store.

## Design

### Root fix: duration belongs with the capability outcome

`CapabilityOutcome` models what happened for a capability (outcome + detail). Duration is
a property of the capability's execution — it belongs here, not as a parallel structure.

### Extracted abstraction: DurationStats

Both the overall case stats and per-capability stats share the same shape: median, min, max,
sample count. `DurationStats` captures this once. `SlaEstimate` composes it.

### Storage: reuse existing table, add index

The `sla_calibration_record` table already has a `capability` column indexed with
`(capability, scope_path, computed_at DESC)`. Today it stores one row per calibration run
with `capability = "pr-review"`. Per-capability rows use the same table with
`capability = "code-analysis"`, `"security-review"`, etc. — all sharing the same `caseId`
and `computedAt` from the same calibration run.

A new index `(scope_path, computed_at DESC)` is needed for `findLatestCalibration` — the
existing index leads with `capability`, so scope-only queries can't use it efficiently.
This requires a migration (`V103`).

## Changes by Layer

### devtown-domain (pure Java)

**`CapabilityOutcome`** — add `Duration duration` field:

```java
public record CapabilityOutcome(String outcome, String detail, Duration duration) {
    public boolean hadFindings() { /* unchanged */ }
}
```

Duration is nullable — `null` when timestamp data isn't available (e.g. the transient
`MemoryContext.hasRisk()` usage). All existing call sites update to pass `null` where
duration is not relevant.

**New `DurationStats`** — reusable statistical summary:

```java
public record DurationStats(Duration median, Duration min, Duration max, int sampleCount) {
    public Map<String, Object> toMap() {
        return Map.of(
            "medianSeconds", median.toSeconds(),
            "minSeconds", min.toSeconds(),
            "maxSeconds", max.toSeconds(),
            "sampleCount", sampleCount,
            "precedentCount", sampleCount);
    }
}
```

`toMap()` emits both `sampleCount` (new canonical name) and `precedentCount` (backward
compatibility with existing context map consumers).

**`SlaEstimate`** — refactored to compose `DurationStats`:

```java
public record SlaEstimate(
    DurationStats overall,
    Map<String, DurationStats> capabilityBreakdown
) {
    public Map<String, Object> toContextMap() {
        var map = new LinkedHashMap<>(overall.toMap());
        if (!capabilityBreakdown.isEmpty()) {
            var breakdown = new LinkedHashMap<String, Object>();
            capabilityBreakdown.forEach((k, v) -> breakdown.put(k, v.toMap()));
            map.put("capabilityBreakdown", breakdown);
        }
        return map;
    }
}
```

Breaking change: callers switch `estimate.median()` → `estimate.overall().median()`,
`estimate.precedentCount()` → `estimate.overall().sampleCount()`.

**`SlaEstimator.estimate()`** — compute both overall and per-capability:

```java
public static Optional<SlaEstimate> estimate(List<Precedent> precedents) {
    // Overall (existing logic)
    List<Duration> totalDurations = precedents.stream()
        .map(Precedent::completionTime)
        .filter(Objects::nonNull)
        .filter(d -> !d.isNegative() && !d.isZero())
        .sorted().toList();
    if (totalDurations.isEmpty()) return Optional.empty();
    DurationStats overall = statsFrom(totalDurations);

    // Per-capability (new)
    Map<String, List<Duration>> perCap = new LinkedHashMap<>();
    for (Precedent p : precedents) {
        for (var entry : p.capabilityOutcomes().entrySet()) {
            Duration d = entry.getValue().duration();
            if (d != null && !d.isNegative()) {
                perCap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(d);
            }
        }
    }
    Map<String, DurationStats> breakdown = new LinkedHashMap<>();
    perCap.forEach((cap, durations) -> {
        List<Duration> sorted = durations.stream().sorted().toList();
        breakdown.put(cap, statsFrom(sorted));
    });

    return Optional.of(new SlaEstimate(overall, breakdown));
}

private static DurationStats statsFrom(List<Duration> sorted) {
    return new DurationStats(
        sorted.get(sorted.size() / 2), sorted.getFirst(),
        sorted.getLast(), sorted.size());
}
```

### devtown-review (integration layer)

**`SlaCalibrationStore`** — add batch operations:

```java
public interface SlaCalibrationStore {
    void save(SlaCalibrationRecord record);
    void saveAll(List<SlaCalibrationRecord> records);
    Optional<SlaCalibrationRecord> findLatest(String capability, String scopePath);
    List<SlaCalibrationRecord> findLatestCalibration(String scopePath);
}
```

`saveAll` persists the full calibration batch atomically. `findLatestCalibration` returns
all records sharing the most recent `computedAt` for a scope — the complete snapshot.

**`SlaCalibrationRecord`** — no changes. Existing shape handles per-capability rows.

### devtown-app (CDI wiring)

**`DefaultCbrRetrievalService.enrichOutcomes()`** — accept `startedAt`, compute duration:

```java
private EnrichmentResult enrichOutcomes(UUID caseId, String contributor,
                                         String tenantId, Instant startedAt) {
    // ... query memory facts (unchanged) ...
    for (var fact : outcomeFacts) {
        String capability = fact.attributes().get(DevtownMemoryKeys.CAPABILITY);
        String outcome    = fact.attributes().get(MemoryAttributeKeys.OUTCOME);
        String detail     = fact.attributes().get(DevtownMemoryKeys.OUTCOME_DETAIL);
        if (capability != null && outcome != null) {
            Duration duration = null;
            if (startedAt != null && fact.createdAt() != null) {
                Duration raw = Duration.between(startedAt, fact.createdAt());
                if (!raw.isNegative()) duration = raw;
            }
            outcomes.put(capability, new CapabilityOutcome(outcome, detail, duration));
        }
    }
    return new EnrichmentResult(outcomes, latestOutcome);
}
```

Caller passes `cv.startedAt()`:
```java
EnrichmentResult enrichment = enrichOutcomes(cv.caseId, cv.contributor, tenantId, cv.startedAt());
```

**`PrReviewCaseService.startReview()`** — save per-capability calibration records:

```java
slaEstimate.ifPresent(estimate -> {
    Instant now = Instant.now();
    String scopePath = "casehubio/devtown/pr-review";
    var records = new ArrayList<SlaCalibrationRecord>();
    records.add(new SlaCalibrationRecord(UUID.randomUUID(), "pr-review", scopePath,
        estimate.overall().median(), estimate.overall().min(), estimate.overall().max(),
        estimate.overall().sampleCount(), caseId, now));
    estimate.capabilityBreakdown().forEach((cap, stats) ->
        records.add(new SlaCalibrationRecord(UUID.randomUUID(), cap, scopePath,
            stats.median(), stats.min(), stats.max(),
            stats.sampleCount(), caseId, now)));
    slaCalibrationStore.saveAll(records);
});
```

SLA override updates to `estimate.overall()`:
```java
if (overrideEnabled && estimate.overall().sampleCount() >= minPrecedents) {
    initialContext.put("slaOverride", Map.of(
        "medianSeconds", estimate.overall().median().toSeconds(),
        "precedentCount", estimate.overall().sampleCount(),
        "active", true));
}
```

**`JpaSlaCalibrationStore`** — implement new methods:

```java
@Override @Transactional
public void saveAll(List<SlaCalibrationRecord> records) {
    records.forEach(r -> em.persist(SlaCalibrationEntity.from(r)));
}

@Override
public List<SlaCalibrationRecord> findLatestCalibration(String scopePath) {
    return em.createQuery(
        "SELECT e FROM SlaCalibrationEntity e WHERE e.scopePath = :scope " +
        "AND e.computedAt = (SELECT MAX(e2.computedAt) FROM SlaCalibrationEntity e2 " +
        "WHERE e2.scopePath = :scope)", SlaCalibrationEntity.class)
        .setParameter("scope", scopePath)
        .getResultStream()
        .map(SlaCalibrationEntity::toRecord)
        .toList();
}
```

**Migration `V103`** — add index for scope-path-leading queries:

```sql
CREATE INDEX idx_sla_calibration_scope_computed
    ON sla_calibration_record (scope_path, computed_at DESC);
```

## Test Plan

| Test | Layer | Verifies |
|------|-------|----------|
| `DurationStats.toMap()` serializes correctly | domain | Unit |
| `SlaEstimator` computes per-capability breakdown | domain | Unit — 3+ precedents with different capabilities |
| `SlaEstimator` excludes null/negative capability durations | domain | Unit — partial data |
| `SlaEstimator` includes zero-duration capabilities | domain | Unit — fast capabilities not dropped |
| `SlaEstimator` handles precedents with no capability outcomes | domain | Unit — empty breakdown |
| `SlaEstimate.toContextMap()` includes breakdown | domain | Unit — nested structure |
| `JpaSlaCalibrationStore.saveAll` + `findLatestCalibration` | app | @QuarkusTest — save batch, retrieve all |
| `findLatestCalibration` returns latest batch only | app | @QuarkusTest — two batches, verify latest |

## Not In Scope

- Per-capability SLA overrides (future — uses this data)
- Active execution time tracking (requires dispatch timestamps not yet in memory facts)
- MCP tool changes for exposing breakdown (separate issue if needed)
- Serialize per-capability duration in `MemoryContext.toContextMap()` (#175)
- Document sample count divergence between overall and per-capability (#176)

## Design Review Findings

Reviewed 2026-08-01 (light depth, all dimensions + cross-cutting).

**Accepted:** index needed for `findLatestCalibration` (V103 migration added),
`startedAt` bias documented, zero-duration filter relaxed for per-capability data,
`precedentCount` backward compat key preserved in `toMap()`.

**Rejected:** `findLatestCalibration` no-caller (completes persistence layer),
redundant `completionTime` (measures different thing), `SlaCalibrationRecord`
compose `DurationStats` (over-abstraction for persistence record), concurrent
timestamp collision (near-impossible, no production caller yet), `saveAll`
transaction boundary (pre-existing pattern).

**Deferred:** #175, #176.
