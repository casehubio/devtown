# Demo Readiness & Production Path — Design Capture

> **Date:** 2026-10-04
> **Epic:** #208
> **Branch:** wip/demo-readiness

---

## 1. What DevTown Is — The Value Proposition

DevTown is not a notification dashboard for PRs. GitHub already does that.

DevTown is an **automated code review judgment engine** that:

1. **Analyzes code content** to decide what KIND of review each PR needs (security? architecture? just style?)
2. **Selects the best reviewer** for each type based on trust history — not CODEOWNERS, not manual assignment
3. **Dispatches AI reviewers in parallel** across multiple capabilities simultaneously
4. **Surfaces findings with context** — not just "this line is bad" but "this is similar to a past case that caused an incident"
5. **Escalates to humans only when needed** — with SLA enforcement, not open-ended "please review"
6. **Learns from outcomes** — trust scores update from every review, changing future routing automatically
7. **Merges through a trust-weighted queue** — high-trust contributor PRs get priority

The demo must show this automated judgment, not tables of state.

---

## 2. The Intelligence Layer

DevTown provides intelligence that GitHub cannot. This intelligence is already built in the backend — the gap is surfacing it in the UI.

### Contributor Intelligence
- Trust score, merge rate, first-attempt quality, intake lane
- Prior security findings on similar code
- GitHub profile data (merge history, contribution patterns)
- **Built in:** `ContributorDetail`, `GitHubIntelligence`, `ContributorGitHubProfileEntity`

### Code Intelligence
- CBR precedents (analogous past PRs and what happened to them)
- Code analysis classification (security-sensitive, architecture-crossing)
- Flagged files with reasons
- **Built in:** `CbrRetrievalService`, `CodeAnalysisResult`, `PrFeatureVector`

### Reviewer Intelligence
- Why THIS reviewer was selected for THIS review
- Their trust score for THIS specific capability
- False-positive rate, historical accuracy
- Decision history on similar code
- **Built in:** `ReviewerHealth`, `TrustGateService`, routing strategy chain

### Policy Intelligence
- Which CasePlanModel bindings fired and why
- What threshold/policy caused a specific routing decision
- SLA estimates from precedent cases
- **Built in:** `GovernancePreferenceKeys`, `SlaCalibrationRecord`, `RoutingPolicy`

### Temporal Intelligence
- Prior decisions on this same file/module
- Incident history related to this code area
- Trend data (is this area getting better or worse?)
- **Built in:** `CaseMemoryStore`, `IncidentFeedbackService`

---

## 3. Interaction Model — Where Humans Interact

### GitHub is the primary interaction surface — the PR is the shared workspace

| Persona | Interacts via | Identity source |
|---------|--------------|-----------------|
| **Contributors** | GitHub (open PRs) | `pull_request` webhook: `user.login`, `user.id` |
| **Human reviewers** | GitHub (submit review: approve/request changes) | `pull_request_review` webhook: `review.user.login` |
| **Security/arch specialists** | GitHub (requested review → submit review) | Same webhook, devtown checks GitHub team membership |
| **Ops leads** | DevTown dashboard (read-only monitoring) | OIDC / GitHub OAuth |

### DevTown writes back to GitHub

DevTown doesn't just receive webhooks — it writes back:
- **Check runs** per review capability (`devtown/security-review`, `devtown/style-review`)
- **Review requests** to GitHub teams when human gates fire
- **PR comments** with actionable findings and precedent context
- **Commit status** (`devtown/review-status`) for branch protection integration

This makes devtown visible on every PR without leaving GitHub.

### AI agents post findings as PR comments

Review agents write their findings as **inline PR review comments** on the specific lines they flagged. This makes the PR the shared workspace between AI and human reviewers:

- Other LLM agents can see each other's findings via PR comments
- Human reviewers see findings in context alongside the diff — not in a separate dashboard
- The PR comment thread is the review conversation — AI and humans participate on equal footing
- The dashboard provides depth (trust, precedents, routing rationale) for complex decisions

Gas Town and Gas City do NOT make their workflow visible on GitHub — they treat it as inbound-only (webhooks) and outbound-only (merge). DevTown posting findings as PR comments is a differentiator.

### The dashboard is the intelligence workbench

The dashboard is NOT where reviewers take actions — the PR is. The dashboard is where they see the INTELLIGENCE behind decisions:

- Case timeline with clickable decision nodes
- Reviewer selection rationale (trust scores, alternatives)
- Contributor profiles with historical intelligence
- CBR precedent comparison
- SLA tracking and escalation status
- Trust evolution over time

This intelligence can also be injected into GitHub PR pages via Chrome extension (future).

---

## 4. Production Deployment Path

### Phase 1: Shadow mode
Deploy alongside existing workflow. Receive webhooks, run cases, accumulate trust — but don't merge or post to GitHub. Validates the engine without risk.

### Phase 2: Read-only production
Enable GitHub write-back (check runs, comments). PRs show devtown findings and status. Humans still merge manually on GitHub. Validates the intelligence layer.

### Phase 3: Guarded production
Enable merge queue admission via label. Merge execution gated by human approval (WorkItem). The full system runs with a human safety net.

### Phase 4: Full autonomous
Remove the human gate for merge. Trust scores drive routing autonomously. Only after sufficient trust has accumulated (Phase 3 maturity model).

### Infrastructure

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Database | PostgreSQL | Already configured in Flyway, H2 is dev-only |
| Deployment | GraalVM native image on Cloud Run or K8s | 0.084s startup |
| GitHub auth | GitHub App (not PAT) | Org-level access, auto-rotating tokens, posts as `devtown[bot]` |
| Dashboard auth | OIDC with GitHub OAuth | Users log in with GitHub accounts |
| LLM for reviewers | Claude API via AgentProvider | Review agents need LLM access |

---

## 5. Multi-User Testing

### The problem
One GitHub account can only simulate one user. PR review lifecycle requires multiple identities (contributor, reviewer A, reviewer B).

### The solution: Webhook replay
Dev-mode endpoint accepts GitHub webhook payloads with identity override. Routes through real `GitHubWebhookResource.receive()` processing. Same code path as production, different identity.

Preset scenarios for common flows:
- alice opens PR → bob reviews → charlie approves
- Exercises trust differentiation, routing decisions, reviewer selection

---

## 6. UI Architecture

### Current state (9 tabs, 3 blocks-ui components used)
- Operations, Reviews tabs: ~80% overlap, tables of state
- Triage: read-only, no actions
- Workers: stub returning empty list
- Only 3 of ~20 available blocks-ui components wired

### Target state (7 tabs, ~20 blocks-ui components wired)

| Tab | Primary component | Purpose |
|-----|-------------------|---------|
| **Review Workbench** | `blocks-timeline` + `case-explorer` + `routing-rationale` + `similarity-panel` + `detail-pane` | Case timeline + intelligence pane. The demo view. |
| **Operations** | `execution-monitor` + `kpi-metric-row` + `notification-inbox` | System-level monitoring. |
| **Merge Queue** | Existing + `commitment-viz` | Queue visualization. |
| **Reviewers** | `trust-workbench` + `trust-score-panel` + `trust-feedback-display` | Reviewer trust and performance. |
| **Contributors** | `contributor-workbench` + `trust-score-panel` | Contributor intelligence. |
| **System** | `compliance-summary` + `audit-trail-viewer` + `sla-indicator` | Health, compliance, SLA. |
| **Definitions** | `blocks-plan-model-dashboard` + `blocks-plan-item-tree` | CasePlanModel viewer. |

### Key UI components to wire (already built in blocks-ui)

**Case lifecycle:** `blocks-timeline`, `case-explorer`, `blocks-plan-item-tree`, `routing-rationale`, `similarity-panel`, `execution-monitor`

**Human gates:** `work-item-workbench`, `work-item-inbox`, `work-item-detail`, `approval-gate`, `sla-indicator`

**Trust and compliance:** `trust-score-panel`, `trust-feedback-display`, `commitment-viz`, `audit-trail-viewer`, `compliance-summary`

**Layout:** `split-workbench`, `detail-pane`, `list-pane`, `kpi-metric-row`, `notification-inbox`

---

## 7. Gap Analysis Summary

### Backend gaps (simulation + production)

| # | Issue | Type | Scale |
|---|-------|------|-------|
| #209 | GitHub approved reviews don't advance the case | bug | M |
| #210 | Register ci-runner worker or remove run-ci binding | bug | S |
| #211 | WorkItem completion API — claim, decide, complete | enhancement | M |
| #212 | Dev-mode PrDiffService stub — synthetic diffs | enhancement | S |
| #213 | Dev-mode CI pass-through — auto-pass CI | enhancement | S |
| #214 | Fix dev-mode action buttons | bug | S |

### GitHub integration gaps

| # | Issue | Type | Scale |
|---|-------|------|-------|
| #217 | GitHub write-back — check runs, review requests, comments | enhancement | M |
| #218 | GitHub App setup and authentication | enhancement | M |
| #219 | Webhook replay endpoint for multi-user testing | enhancement | S |
| #220 | Dashboard authentication — OIDC with GitHub OAuth | enhancement | M |

### UI gaps

| # | Issue | Type | Scale |
|---|-------|------|-------|
| #216 | Review Intelligence Workbench — timeline + intelligence pane | enhancement | L |
| #221 | Wire existing blocks-ui components (20+ unused) | enhancement | L |

### Simulation

| # | Issue | Type | Scale |
|---|-------|------|-------|
| #215 | Simulation script rewrite — full lifecycle with phases | enhancement | L |

---

## 8. What's Already Built But Not Wired

This is the most important finding from the session. The gap is not "build things" — it's "connect things."

### Backend: fully built, REST-exposed, unused by UI

| Endpoint | Service | What it provides |
|----------|---------|-----------------|
| `/api/devtown/trust/{actorId}` | `TrustQueryService.trustScore()` | Per-capability and per-dimension trust scores |
| `/api/devtown/trust/{actorId}/trend` | `TrustQueryService.trustTrend()` | Trust evolution over time (needs snapshot entity fix) |
| `/api/devtown/trust/{actorId}/routing-history` | `TrustQueryService.routingHistory()` | Every routing decision with scores |
| `/api/devtown/trust/{actorId}/routing-history/{id}` | `TrustQueryService.routingDetail()` | Full rationale: selected candidate, alternatives, policy, feedback |
| `/api/devtown/reviews/{caseId}` | `GovernanceQueryService.reviewDetail()` | Case detail: timeline, capabilities, plan items |
| `/api/devtown/reviews/contributors/{actorId}` | `GovernanceQueryService.contributorDetail()` | Full contributor intelligence including GitHub profile |
| `/api/devtown/governance/recent-events` | `GovernanceQueryService.recentEvents()` | Event stream with case/actor context |
| `/api/devtown/governance/sla-comparison` | `GovernanceQueryService.slaComparison()` | SLA calibration from precedents |

### blocks-ui: 20+ components built, 3 used

| Category | Components available | Used by devtown |
|----------|---------------------|-----------------|
| Case lifecycle | `blocks-timeline` (3588 lines), `case-explorer` (3083), `blocks-plan-item-tree`, `routing-rationale` (581), `similarity-panel` (291), `execution-monitor` (291) | None |
| Human gates | `work-item-workbench` (349), `work-item-detail` (2154), `approval-gate` (1050), `sla-indicator` (408) | None |
| Trust | `trust-score-panel` (880), `trust-feedback-display` (307) | None (trust-workbench is used but these aren't) |
| Compliance/audit | `compliance-summary` (279), `audit-trail-viewer` | None |
| Layout | `split-workbench`, `detail-pane`, `list-pane`, `kpi-metric-row`, `notification-inbox` | None |
| Used | `session-workbench`, `trust-workbench`, `contributor-workbench` | 3 of ~23 |

### Only 2 new REST endpoints needed
1. CBR similarity — expose `CbrRetrievalService` for a case
2. Compliance summary — expose casehub-ops posture (if on classpath)

---

## 9. Execution Order

### Batch 1 — Backend fixes (no dependencies, can parallel)
#209, #210, #211, #212, #213

### Batch 2 — Depends on Batch 1
#214 (depends on #209, #211)

### Batch 3 — UI + Integration (can parallel after Batch 1)
#216, #221 (depend on #211 for work-item endpoints)
#217 (GitHub write-back — independent)
#218 (GitHub App — independent, enables #217)
#219 (webhook replay — depends on webhook resource existing)

### Batch 4 — Full integration
#215 (simulation script — depends on everything above)
#220 (dashboard auth — can be done independently but validates last)
