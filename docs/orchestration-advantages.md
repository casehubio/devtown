# Beyond Workflow: CaseHub Orchestration and Choreography

> **Audience:** Technical engineers evaluating CaseHub against workflow engines and agent coordination systems.
>
> **Purpose:** Concrete demonstration of where Adaptive Case Management (ACM) produces measurably better outcomes than workflow-based coordination — specifically in the AI-assisted software development domain.

---

## The Core Distinction

Workflow engines (Temporal, Camunda, BPMN, Gastown formulas) answer one question: *what steps must be executed?* The structure is fixed at design time. You declare steps, branches, and parallel paths. The engine executes your declaration.

Adaptive Case Management answers a different question: *what needs to be achieved?* The structure emerges at runtime from what agents discover. Goals are declared. The engine finds the path.

For software development workflows where the appropriate review, testing, and approval process depends on what the code actually contains — not what the author said it contains — this distinction produces concrete, measurable differences in outcome.

---

## 1. PR Review Adapts to What's Actually in the Code

### The problem with workflow-based routing

In a workflow engine, routing decisions are made at design time or based on metadata declared by the PR author (labels, file paths, PR description). If an author doesn't flag that they touched cryptographic code, the workflow assigns the wrong reviewers. Correcting this requires the assigned reviewer to recognise the mismatch and manually re-route — adding latency and depending on the reviewer's domain awareness.

### How CaseHub handles it

The case declares a **goal**, not a sequence:

```yaml
goals:
  - id: pr-approved
    condition: ".reviews | map(select(.verdict=='APPROVED')) | length >= requiredApprovals"
  - id: security-verified
    condition: ".securityFindings == null or .securityFindings.criticalCount == 0"
  - id: ci-passing
    condition: ".ci.status == 'passing'"
```

Bindings fire reactively based on what analysis workers find on the shared blackboard:

```yaml
bindings:
  - name: initial-analysis
    on: { contextChange: {} }
    when: ".pr != null and .analysisComplete == null"
    capability: "code-analysis"

  - name: security-review
    on: { contextChange: {} }
    when: ".analysisComplete == true and (.analysisFindings.cryptographic == true or .analysisFindings.authCode == true)"
    capability: "security-review"

  - name: standard-review
    on: { contextChange: {} }
    when: ".analysisComplete == true"
    capability: "code-review"
```

The security review binding **only fires if the code analysis finds security-sensitive code**. If there's nothing cryptographic or authentication-related, the case routes directly to standard review and merge. No re-slinging. No human decision. The routing logic lives in the case definition, applied consistently to every PR.

### Concrete benefit

- Reviewers with specialist expertise are pulled in precisely when their expertise is relevant, not based on what humans predicted when designing the workflow
- Authors cannot accidentally bypass security review by omitting labels — the analysis determines the routing, not the metadata
- Routing decisions are in the audit trail — every case records why it routed to security review

---

## 2. Human Approval Runs in Parallel with Automated Checks

### The problem with sequential gates

In most workflow systems and in Gastown, human review is a sequential step: automated checks run, then a human reviews, then merge happens. A 30-minute CI suite and a 45-minute human review cycle cost `30 + 45 = 75` minutes per PR.

### How CaseHub handles it

The WAITING state is durable and explicit. Both the human approval and the CI suite fire as soon as their binding conditions are met — which may be simultaneously:

```yaml
bindings:
  - name: request-architectural-approval
    on: { contextChange: {} }
    when: ".pr.linesChanged > 500 and .architecturalApproval == null"
    capability: "human-approval-gate"

  - name: run-ci
    on: { contextChange: {} }
    when: ".pr != null and .ci == null"
    capability: "ci-runner"

  - name: merge
    on: { contextChange: {} }
    when: ".architecturalApproval.status == 'approved' and .ci.status == 'passing' and .reviews | length >= 2"
    capability: "merge-executor"
```

When the PR arrives, both `request-architectural-approval` and `run-ci` fire simultaneously. The case enters WAITING for the architectural approval (durable across restarts via `PendingWorkRegistry`) while CI runs in parallel. The merge binding fires when *both* conditions are satisfied — in whatever order they complete.

If CI fails while waiting for human approval, the case can adapt:

```yaml
  - name: cancel-on-ci-failure
    on: { contextChange: {} }
    when: ".ci.status == 'failing' and .architecturalApproval == null"
    capability: "notify-and-cancel"
```

### Concrete benefit

- Total time is `max(CI duration, human review duration)` rather than their sum
- For a typical 30-minute CI and 45-minute architectural review: **37% reduction in cycle time**
- The human approval gate doesn't add latency to work that can run concurrently
- Failure during human waiting is handled automatically, not manually

---

## 3. Automatic Parallelism — Declared Once, Exploited Everywhere

### The problem with explicit parallelism

In workflow systems, parallel execution must be declared explicitly. Developers must think about which steps can run concurrently and model it in the workflow definition. Missing a parallelism opportunity means sequential execution by default.

### How CaseHub handles it

All bindings whose conditions are simultaneously satisfied fire at once — no declaration required. When a PR arrives and all the pre-conditions for multiple check types are met, all checks start simultaneously:

```yaml
bindings:
  - name: security-check
    when: ".pr != null and .securityCheck == null"
    capability: "security-review"

  - name: style-check
    when: ".pr != null and .styleCheck == null"
    capability: "style-linter"

  - name: test-coverage
    when: ".pr != null and .coverageCheck == null"
    capability: "coverage-analyzer"

  - name: performance-check
    when: ".pr != null and .performanceCheck == null"
    capability: "performance-analyzer"

  - name: merge
    when: >
      .securityCheck.passed == true and
      .styleCheck.passed == true and
      .coverageCheck.passed == true and
      .performanceCheck.passed == true
    capability: "merge-executor"
```

All four checks start simultaneously when the PR arrives. The merge binding fires when all four are satisfied — in whatever order they complete. Adding a fifth check type requires one new binding definition. No restructuring of the parallel declaration.

### Concrete benefit

- Time-to-review is bounded by the **slowest check**, not the sum of all checks
- Adding new check types requires one binding definition, not restructuring parallel declarations
- The case automatically discovers and exploits parallelism without the developer declaring it

---

## 4. Cross-Repo Changes as First-Class Orchestrated Cases

### The problem with per-repo coordination

A refactoring that spans multiple repositories requires coordinating multiple PR review processes. In Gastown, this is cross-rig coordination: the Mayor creates beads in each rig and informally tracks completion. If one repo's CI fails, the Mayor or a human must notice and coordinate rollback of the others.

### How CaseHub handles it

A parent case spawns sub-cases — one per repository. Each sub-case is a full ACM instance with its own goals and blackboard. The parent case tracks sub-case completion as binding conditions:

```yaml
# Parent case
bindings:
  - name: spawn-api-pr
    when: ".apiPrCreated == null"
    capability: "pr-creator"
    workerContext: { repo: "api-service", branch: "feat/refactor-auth" }

  - name: spawn-frontend-pr
    when: ".frontendPrCreated == null"
    capability: "pr-creator"
    workerContext: { repo: "frontend", branch: "feat/refactor-auth" }

  - name: merge-all
    when: >
      .apiSubCase.status == 'COMPLETED' and
      .frontendSubCase.status == 'COMPLETED'
    capability: "coordinated-merge"

  - name: rollback-on-failure
    when: >
      (.apiSubCase.status == 'FAULTED' or .frontendSubCase.status == 'FAULTED')
    capability: "coordinated-rollback"
```

If any sub-case faults (CI failure, security finding, human rejection), the rollback binding fires automatically. Every case in the coordination chain is auditable — the parent EventLog records every sub-case state transition and every routing decision.

### Concrete benefit

- Multi-repo changes are a first-class, auditable, formally tracked concern
- Failures propagate and trigger responses automatically — no human has to notice and coordinate rollback
- The coordination logic is declared once in the parent CasePlanModel and applied consistently

---

## 5. Failure Handling Is Declarative and Consistent

### The problem with agent-side failure handling

When an agent fails in Gastown, Witness detects the timeout and re-assigns the bead. The re-assignment follows the same formula step regardless of *why* the agent failed. Distinguishing "security agent couldn't do this PR (outside capability)" from "security agent crashed (technical failure)" requires reading agent output or logs — and acting on the distinction requires custom code in the agent or the overseer.

### How CaseHub handles it

Failure is a **fact on the blackboard**. Bindings evaluate against failure facts and can route differently based on failure type:

```yaml
bindings:
  - name: activate-backup-security-reviewer
    when: >
      .primarySecurityReview.outcome == 'FAILED' and
      .backupSecurityReview == null
    capability: "security-review-backup"

  - name: reduce-scope-on-double-failure
    when: >
      .primarySecurityReview.outcome == 'FAILED' and
      .backupSecurityReview.outcome == 'FAILED'
    capability: "scoped-security-review"
    workerContext: { scope: "flagged-files-only" }

  - name: escalate-to-human-security
    when: >
      .primarySecurityReview.outcome == 'DECLINED' or
      (.primarySecurityReview.outcome == 'FAILED' and
       .backupSecurityReview.outcome == 'FAILED' and
       .scopedSecurityReview.outcome == 'FAILED')
    capability: "human-security-escalation"
```

- `DECLINED` (agent chose not to — outside capability) routes immediately to human escalation
- `FAILED` (agent tried and couldn't) routes to backup first, then reduces scope, then escalates

The difference between DECLINED and FAILED is critical: one means the agent is fine but the task needs a different agent; the other means something may be wrong with the agent. CaseHub's normative layer makes this distinction structurally — it is never inferred from free text.

### Concrete benefit

- Failure handling is declared once in the case definition, consistent across every PR
- The system automatically distinguishes capability mismatch from technical failure and responds appropriately
- No agent needs to implement recovery logic — it's in the case structure

---

## 6. The Merge Strategy Is Auditable and Changeable Without Deployment

### The problem with strategy-as-code

Gastown's Refinery implements batch-then-bisect as Go code. The strategy is in the implementation. Changing it means changing and deploying the Refinery. Different repos cannot easily have different strategies. Every strategy decision is implicit in code execution — not recorded as a case fact.

### How CaseHub handles it

The merge strategy is **binding conditions in a CasePlanModel**. The batch-then-bisect strategy:

```yaml
bindings:
  - name: test-batch-tip
    when: ".batch.size > 0 and .tipTest == null"
    capability: "ci-runner"
    workerContext: { target: "tip-of-batch" }

  - name: merge-batch
    when: ".tipTest.status == 'passing'"
    capability: "batch-merge"

  - name: bisect
    when: ".tipTest.status == 'failing' and .batch.size > 1"
    capability: "batch-bisect"

  - name: reject-single-failing-pr
    when: ".tipTest.status == 'failing' and .batch.size == 1"
    capability: "pr-reject-and-notify"
```

A different strategy (trisect, random sampling, priority-ordered) is a different CasePlanModel — selected at case creation time, not at deployment time. The repo's strategy is a configuration choice. Every strategy decision (why this batch was bisected, which PR caused the failure) is in the EventLog and queryable:

```
get_causal_chain(correlationId: "merge-batch-20260429")
→ COMMAND(test tip) → FAILURE(PR#456 breaks auth tests) → COMMAND(bisect) → ...
```

### Concrete benefit

- Strategy changes don't require deployment
- Different repos can use different merge strategies configured at case creation
- Every merge decision is auditable — *why* a batch was bisected, *which PR* caused the failure, *who* approved the merge

---

## 7. LLM Supervisor for Genuinely Open-Ended Work

### The problem with fixed workflows for exploratory work

Some software engineering tasks don't have a predetermined structure. A large codebase analysis, an architectural audit, a security assessment — the next step depends on what was found in the previous step. Workflow engines require you to pre-enumerate all possible paths. For exploratory work, this is impossible.

### How CaseHub handles it

The `LlmPlanningStrategy` SPI enables an LLM to read the current `CaseContext` and dynamically select the next binding:

```java
// LlmPlanningStrategy reads the blackboard and selects what to do next
List<Binding> selected = planningStrategy.select(
    caseContext,           // everything found so far
    eligibleBindings,      // bindings whose pre-conditions are met
    workerCapabilities     // what agents are available and their trust scores
);
```

For a codebase security audit:

1. LLM reads: `codebase = {languages: [Java, Python], size: "large", lastAudit: "2024-01"}` 
2. LLM selects: start with dependency audit (highest risk surface for Java + Python)
3. Dependency audit finds: 3 outdated dependencies with known CVEs
4. LLM reads updated blackboard, selects: deep audit of authentication code (CVEs affect auth libraries)
5. Auth audit finds: timing vulnerability in token validation
6. LLM reads, selects: generate fix proposal + notify security team

Each decision is recorded on the blackboard. The trust model scores the planning LLM's decisions over time — a planning strategy that consistently finds critical issues earns higher routing priority. A strategy that consistently misses things gets routed away from high-stakes audits.

### Concrete benefit

- Exploratory, knowledge-intensive work can be coordinated without pre-enumerating all possible paths
- The LLM's planning decisions are auditable (on the blackboard) not implicit
- The trust model applies to planning strategies as well as execution agents

---

## Summary: Where the Gap Widens with Complexity

| Scenario | Workflow engine | CaseHub ACM |
|----------|----------------|-------------|
| Simple, well-understood PR review | Equivalent | Equivalent |
| PR where routing depends on findings | Manual re-routing required | Automatic — binding fires on analysis result |
| Human approval + automated checks | Sequential by default | Parallel — max(human, CI) not sum |
| 5 parallel check types | Explicit declaration required | Automatic from binding conditions |
| Cross-repo coordinated change | Manual multi-rig coordination | First-class sub-case composition |
| Failure: wrong agent assigned | Re-assign to same step | Different binding fires based on failure type |
| Failure: agent crashed | Same re-assign logic | Different binding — investigate agent |
| Strategy change | Requires deployment | CasePlanModel config change |
| Exploratory/open-ended work | Cannot model without pre-enumerating paths | LlmPlanningStrategy selects dynamically |

**The pattern:** for simple, well-understood workflows, workflow engines are equivalent and often simpler to configure. As complexity increases — conditional routing, parallel coordination, failure adaptation, exploratory work — the ACM approach produces increasingly better outcomes. The gap is not in the feature set; it is in where the intelligence lives. Workflow: intelligence in the workflow definition, written once at design time. CaseHub: intelligence distributed between binding conditions, agent outputs, and the blackboard — evaluated continuously as the case progresses.

---

## Current Status: The One Remaining Gap

CaseHub provides all the advanced orchestration and choreography described above. There is one gap that must be closed before CaseHub also matches Gastown's most basic capability — **assign the right agent to the right work and know whether it completed**.

### Two levels of completion tracking

**Case-level tracking — works today.**
When a binding fires and a worker completes, it writes results to the blackboard. The binding conditions re-evaluate. If the goal is satisfied, the case progresses. This is functional and correct for the goal-level question: *did the outcome we needed get produced?*

The gap: if the agent silently fails before doing anything — provisioned but never picks up the work, crashes before acknowledging — the case simply stalls. You can detect this via `list_stalled_obligations` on the Qhorus side, but the two are not connected. The case engine does not automatically know the agent went silent.

**Normative tracking — needs [engine#186](https://github.com/casehubio/engine/issues/186).**
When work is assigned, a Qhorus COMMAND is sent. A Commitment is created. The lifecycle is:

```
COMMAND sent → Commitment OPEN
Agent starts  → ACKNOWLEDGED  (actively working — not just queued)
Agent done    → FULFILLED     (formal completion, trust scoring fires)
Agent refuses → DECLINED      (re-route immediately, agent is healthy)
Agent crashes → EXPIRED       (stall detection fires, case knows to investigate)
```

The case engine knows exactly what happened and why. Trust scoring fires automatically from the outcome. The distinction between DECLINED (wrong agent, find another) and FAILED/EXPIRED (agent problem, investigate) is structurally captured — not inferred from free text or timeouts.

### Why this matters

This is not a normative-layer nicety. This is what closes the gap between CaseHub and Gastown's most fundamental guarantee: *if work is on your hook, you process it — and the system knows whether you did.*

Until engine#186 ships, CaseHub's advanced orchestration and choreography runs on a foundation that cannot distinguish "agent acknowledged and is working" from "agent never picked up the work." Everything else in this document is more valuable once that baseline is solid.

**engine#186 is the current P0 priority item.** See [casehubio/engine#186](https://github.com/casehubio/engine/issues/186).

---

*Part of the [CaseHub platform documentation](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md).*  
*Application layer: [casehub-devtown](https://github.com/casehubio/devtown)*  
*Engine issues: [casehubio/engine#102](https://github.com/casehubio/engine/issues/102) — full use case pattern list*
