# SlaBreachHandlerWiringTest — Design Spec

**Issue:** casehubio/devtown#42
**Date:** 2026-05-25

---

## Problem

`SlaBreachLifecycleTest` covers the full SLA breach chain end-to-end, but a failure there
can be caused by many things: WorkItem not created, YAML config wrong, expiry detection
broken, two-tier escalation logic wrong, handler wiring broken. A focused wiring test gives
faster, clearer failure messages specifically when CDI displacement breaks.

---

## What Needs Wiring Verification

Two independent CDI concerns in devtown-app:

1. **CDI displacement:** `SlaBreachPolicyBean @ApplicationScoped` (no `@DefaultBean`) must
   displace `NoOpSlaBreachPolicy @DefaultBean @ApplicationScoped` from casehub-work-runtime.
   If `SlaBreachPolicyBean` is absent or mis-annotated, the runtime's NoOp fires silently
   without escalation.

2. **Handler event routing:** `SlaBreachHandler @Observes SlaBreachEvent` must receive the
   CDI event and call `PrReviewCaseHub.signal()` on `BreachDecision.Fail`. If the annotation
   is removed, changed to `@ObservesAsync`, or the injection of `PrReviewCaseHub` fails,
   the case is never signaled.

---

## Design

Single `@QuarkusTest` class, two test methods. No `@Alternative` static inner classes,
no `selected-alternatives` changes.

### Test 1 — CDI displacement

```java
@Inject SlaBreachPolicy policy;

@Test
void slaBreachPolicyBean_displaces_noOp() {
    assertThat(policy).isInstanceOf(SlaBreachPolicyBean.class);
}
```

CDI resolves the `@Default` `SlaBreachPolicy` bean. `SlaBreachPolicyBean @ApplicationScoped`
(no `@DefaultBean`) beats `NoOpSlaBreachPolicy @DefaultBean @ApplicationScoped`. If
`SlaBreachPolicyBean` is missing or mis-annotated, this test fails at startup
(`UnsatisfiedResolutionException`) or at assertion.

### Test 2 — Handler wiring

```java
@Inject PrReviewCaseHub        caseHub;
@Inject Event<SlaBreachEvent>  breachEvents;
@Inject CaseInstanceRepository caseInstanceRepository;

@Test
void slaBreachHandler_onFail_signalsCaseContext() throws Exception {
    UUID caseId = caseHub.startCase(MINIMAL_CTX).toCompletableFuture().get(5, SECONDS);

    String callerRef = CallerRef.encode(caseId, "human-approval");
    var task = new BreachedTask(UUID.randomUUID(), callerRef,
                                "PR approval", Set.of("pr-leads"));
    var ctx  = new SlaBreachContext(BreachType.CLAIM_EXPIRED, task,
                                    Path.root(), MapPreferences.empty());
    breachEvents.fire(new SlaBreachEvent(ctx, new BreachDecision.Fail("sla-breach")));

    await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
        var instance = caseInstanceRepository.findByUuid(caseId)
                .await().atMost(Duration.ofSeconds(2));
        assertThat(instance.getCaseContext().getPath("humanApproval.status"))
                .isEqualTo("sla-breach");
    });
}
```

`SlaBreachEvent` is fired directly — `ExpiryLifecycleService` is bypassed entirely.
`@Observes` (synchronous CDI) means the handler completes before `fire()` returns, but
`caseHub.signal()` may trigger async engine writes, so `await` is kept.

### Minimal context — why `linesChanged=100`

`MINIMAL_CTX` uses `linesChanged=100` (below `humanApprovalThreshold=500`). With the
pr-review YAML, this means all eight bindings are inactive when the case starts:

- `codeAnalysis`, `ci`, `styleCheck`, `testCoverage`, `performanceAnalysis` are pre-seeded
  (non-null) — suppresses those bindings (PP-20260521-134c38)
- `securitySensitive=false`, `architectureCrossing=false` — suppresses those bindings
- `linesChanged=100 ≤ 500=threshold` — suppresses `human-approval` binding

No async binding activity after `startCase()` resolves. Case stays active because `pr-approved`
goal requires `styleCheck.outcome == "APPROVED"` but it is `PENDING`.

`signal(caseId, "humanApproval", ...)` works without an active plan item — the engine
updates case context regardless. Confirmed by `HumanApprovalLifecycleTest` Checkpoint 5
which signals `styleCheck`, `testCoverage`, `performanceAnalysis` (no corresponding
WorkItems exist for those).

### Why not CapturingBreachPolicy

The issue describes a `CapturingBreachPolicy @Alternative @ApplicationScoped` approach.
This approach is rejected because:

1. `selected-alternatives` is build-time — `CapturingBreachPolicy` would displace
   `SlaBreachPolicyBean` for every test in the file, making Test 1 impossible in the
   same class.
2. It tests `ExpiryLifecycleService → SlaBreachPolicy` dispatch — a casehub-work concern
   already covered by `SlaBreachLifecycleTest` Checkpoints 3–4.
3. It introduces a race condition: driving through `ExpiryLifecycleService` requires
   `linesChanged=600 > threshold`, which fires the `human-approval` binding
   asynchronously.

### Failure modes caught

| Failure | How caught |
|---|---|
| `SlaBreachPolicyBean` absent | Startup `UnsatisfiedResolutionException` |
| `@ApplicationScoped` annotation missing | Startup failure |
| `@Observes` changed to `@ObservesAsync` | Test 2 times out — event not received synchronously |
| `@Inject PrReviewCaseHub` fails | Startup failure |
| `switch` on `Fail` removed or wrong key | Test 2 times out — case context not updated |

### Out of scope

- Two-tier escalation logic → `DefaultSlaBreachPolicyTest` (domain unit test)
- Full expiry detection chain → `SlaBreachLifecycleTest`
- `humanApprovalThreshold` as a Preference rather than case-context field — separate issue
  if desired; requires changes to the YAML binding and case context population

---

## File Location

`app/src/test/java/io/casehub/devtown/app/SlaBreachHandlerWiringTest.java`

No changes to `application.properties` — no new `selected-alternatives` entries.

---

## Protocol Compliance

Follows `spi-testing-alternative-inner-classes.md` intent: test doubles are the pattern
for SPI wiring tests. Here, no `@Alternative` is needed because we fire the CDI event
directly rather than driving through the SPI caller. GE-20260512-c246b0 warnings about
`selected-alternatives` pitfalls are avoided entirely.
