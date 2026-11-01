# ADR-0003: Reject Protocol Lifecycle Hooks for Reviewer Interaction State

**Status:** Rejected
**Date:** 2026-11-01
**Issue:** casehubio/devtown#296

## Context

Qhorus #497 shipped `ProtocolLifecycleHandler` SPI with `onInit` and `onDestroy` callbacks for channel-level protocol state management. The question was whether devtown should use these hooks for review round tracking and revision request chains.

## Decision

Rejected. Devtown continues managing review interaction state through CasePlanModel context and binding conditions.

## Rationale

Protocol lifecycle hooks operate at the qhorus channel level — they track when a named protocol starts and ends on a communication channel between actors. Devtown's review state is managed at the case level through CasePlanModel bindings that fire on context predicates (`.styleCheck == null`, `.securityReview.outcome == "APPROVED"`). These are different abstraction levels.

The CPM already handles the current interaction model: single-shot capability dispatch with outcome policy rerouting on decline/failure, scope reduction on exhaustion, and human escalation as a final tier. Adding protocol lifecycle hooks would create a parallel state management path alongside the CPM bindings with no clear benefit.

If multi-round review (reviewer requests changes → contributor pushes → re-review) is needed in the future, it belongs as a CasePlanModel pattern — a binding that re-fires when the PR diff changes — not as a qhorus protocol hook. The CPM is the right abstraction for case-level state; qhorus protocols are for message-channel-level state.

## Consequences

- Review interaction state remains in the CasePlanModel context
- Multi-round review, if needed, will be implemented as a CPM binding pattern
- Protocol lifecycle hooks remain available for qhorus channel-level concerns (e.g., agent session management)
