---
id: PP-20260618-fc6a53
title: "Four-tier failure cascade: engine reroute loop + application scope reduction + human escalation"
type: pattern
scope: platform
applies_to: "Any CaseHub application using capability-based bindings where DECLINED, FAILED, or EXPIRED outcomes require structured recovery"
severity: important
refs:
  - casehub/case-definition-layers.md
  - casehub/descriptor-handler-pattern.md
created: 2026-06-18
---

When a binding dispatches work to a capability, the worker may DECLINE (capability boundary), FAIL (execution error), or go silent until commitment EXPIRED. The four-tier failure cascade provides structured recovery without hardcoding fallback logic in the application.

## Tier 1-2: Engine-owned reroute loop (OutcomePolicy)

The engine handles re-routing via `OutcomePolicy` on each binding. On DECLINED/FAILED/EXPIRED:
1. Writes structured failure state to the blackboard under the capability's key (`status`, `attempts`, `history`, `excludedAgents`)
2. Adds the failed agent to `excludedAgents`
3. Re-dispatches the same capability with agent exclusion — `AgentRoutingStrategy` reads `excludedAgents` from case context and filters candidates
4. When `attempts >= maxRerouteAttempts`, writes `status: "REROUTES_EXHAUSTED"`

This is generic infrastructure — every harness gets it for free. No application-level bindings fire for Tiers 1-2.

```yaml
outcomePolicy:
  onDecline: REROUTE
  onFailure: REROUTE
  onExpired: REROUTE
  maxRerouteAttempts: 2
```

All capability bindings must declare `conflictResolverStrategy: DEEP_MERGE` so that successful retry output merges into existing failure tracking state rather than overwriting the attempt history.

## Tier 3: Application-owned scope reduction

Fires when reroutes are exhausted and the capability supports scope reduction. Uses `Binding.contextWrite` (JSON Merge Patch — RFC 7396) to update blackboard state before dispatch, and `Binding.inputSchemaOverride` to narrow the input.

`contextWrite` resets `status` to `PENDING` and `excludedAgents` to `[]`, and sets `reducedScope: true`. This prevents Tier 3 re-fire (condition `reducedScope == null` is now false) and Tier 4 race (status is no longer `REROUTES_EXHAUSTED`). Excluded agents reset because a previously-declined agent might handle the narrower scope.

The engine runs a fresh OutcomePolicy reroute loop for the reduced-scope dispatch.

```yaml
- name: <capability>-reduced-scope
  when: '.<capKey>.status == "REROUTES_EXHAUSTED" and .<capKey>.reducedScope == null'
  contextWrite:
    <capKey>:
      status: PENDING
      reducedScope: true
      excludedAgents: []
  capability: <capability>
  inputSchemaOverride: "{ <narrowField>: .<source> }"
  conflictResolverStrategy: DEEP_MERGE
  outcomePolicy: { onDecline: REROUTE, onFailure: REROUTE, onExpired: REROUTE, maxRerouteAttempts: 2 }
```

Not all capabilities support scope reduction. Configure via `FailurePolicy.scopeReductionAllowed` on the descriptor. Capabilities without scope reduction skip Tier 3 — Tier 4 fires directly on `REROUTES_EXHAUSTED`.

## Tier 4: Application-owned human escalation

Fires when all automated tiers are exhausted. For capabilities with scope reduction, fires on `REROUTES_EXHAUSTED AND reducedScope == true`. For capabilities without scope reduction, fires on `REROUTES_EXHAUSTED` directly.

```yaml
- name: <capability>-human-escalation
  when: '.<capKey>.status == "REROUTES_EXHAUSTED" and .<capKey>.reducedScope == true'
  conflictResolverStrategy: DEEP_MERGE
  humanTask:
    title: "<Capability> escalation — all automated reviewers exhausted"
    candidateGroups: [<domain-specific-group>]
    expiresIn: <from FailurePolicy.humanEscalationSla>
    outputMapping: "{ <capKey>: { outcome: . } }"
    outcomes: [APPROVED, REJECTED, BLOCKED]
```

Human outcomes: APPROVED (human did the review — success goals can be satisfied), REJECTED (negative verdict — failure goal fires), BLOCKED (human can't resolve it either — failure goal fires).

`DEEP_MERGE` on the humanTask output path preserves attempt history when the human outcome is written.

## Failure goals

Failure goals are passive observers — they evaluate blackboard state, they don't cause state changes. All three result in `CaseStatus.COMPLETED` with failure outcome metadata, not `FAULTED`. `FAULTED` is reserved for system errors.

- `review-blocked`: any capability outcome is `BLOCKED`
- `review-rejected`: any capability outcome is `REJECTED`
- `review-abandoned`: PR closed or superseded externally

## Blackboard failure state schema

The engine manages this state. Applications observe it via binding conditions and goal predicates.

| Field | Type | Semantics |
|-------|------|-----------|
| `status` | String | `PENDING`, `DECLINED`, `FAILED`, `EXPIRED`, `COMPLETED`, `REROUTES_EXHAUSTED` |
| `outcome` | String | Only on success — domain semantics (APPROVED/REJECTED) |
| `attempts` | int | Total attempts across current reroute cycle |
| `history` | Array | Append-only audit trail of every attempt across all tiers |
| `excludedAgents` | Array | Accumulates within a reroute cycle, resets on scope reduction |
| `reducedScope` | Boolean | Set by Tier 3 contextWrite; null until scope reduction fires |

## SLA breach handling

When a Tier 4 humanTask's SLA expires and the breach decision is `Fail`, the `SlaBreachHandler` resolves the output key from the binding's `outputMapping` and signals `{outcome: "BLOCKED"}`. The handler reads `planItemId` from `CallerRef`, looks up the binding in the case definition, and extracts the top-level key from the JQ output mapping. This is the same path for all humanTask bindings — no hardcoded context keys.

## Engine/application boundary

The engine owns Tiers 1-2 (OutcomePolicy, agent exclusion, structured blackboard writes, DEEP_MERGE). The application owns Tiers 3-4 (scope reduction policy, human escalation groups, SLA durations, failure goals). The `FailurePolicy` on the descriptor is the application-level configuration point.
