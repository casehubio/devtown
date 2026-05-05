# casehub-devtown

AI-assisted software development application built on the [CaseHub](https://github.com/casehubio/parent) platform foundation.

## What This Is

`casehub-devtown` is the first application layer built on CaseHub's domain-agnostic foundation. It applies CaseHub's orchestration, normative accountability, and trust model to the software engineering domain — providing AI-assisted code review, merge queue orchestration, and trusted agent routing for development teams.

**Built on:**
- `casehub-engine` — ACM orchestration (cases, bindings, blackboard)
- `casehub-qhorus` — Agent communication mesh (speech acts, commitments)
- `casehub-ledger` — Trust scoring, audit trail, GDPR compliance
- `casehub-work` — Human review tasks (WorkItem inbox, SLA)
- `casehub-connectors` — Notifications (Slack, Teams, email)

## Current Status

Early development. Foundation capabilities being built in parallel in the ecosystem repos — see [platform roadmap](https://github.com/casehubio/parent/issues/7).

## Platform Context

This repo is part of the [casehubio ecosystem](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md). The foundation layer (casehub-engine, casehub-qhorus, etc.) is domain-agnostic. This application layer provides the software engineering domain logic on top of those primitives.
