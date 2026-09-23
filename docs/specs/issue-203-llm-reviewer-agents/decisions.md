# Design Decisions — LLM Reviewer Agents (#203)

## D1: Dispatch path — ReviewerAgent + CodeAnalysisAgent interfaces (REVISED)

**Choice:** Implement LLM reviewers as `ReviewerAgent` implementations (findings-based) and LLM code-analysis as a `CodeAnalysisAgent` implementation (classification). Wire into the production Layer 5 path via function workers registered in `PrReviewCaseHub.augment()`, following the established `merge-executor` pattern.
**Alternatives:**
- WorkerProvisioner SPI — provisions external workers, not appropriate for in-process function workers
- Standalone service — parallel invocation path, bypasses port interface
- Single `ReviewerAgent` for all capabilities — forces `ReviewerOutcome` to carry classification data it can't model
**Rationale:** Function workers in `augment()` are the established engine dispatch mechanism (verified against `adaptMerge()`). Code-analysis is separated from `ReviewerAgent` because it produces classification data (`CodeAnalysisResult`), not findings — the sealed `ReviewerOutcome` type cannot carry classification fields.
**Trade-offs:** Two port interfaces instead of one. Code-analysis and review capabilities have separate adapter functions.
**Sources:** `review/src/main/java/.../ReviewerAgent.java`, `app/.../PrReviewCaseHub.java`, `app/.../QhorusPrReviewService.java`
**Exploration:** quick
**Status:** captured (revised — original referenced WorkerProvisioner; settled on function workers via R1-03; split CodeAnalysisAgent via R2-01)

## D2: Structured findings model — ReviewFinding domain record

**Choice:** New `ReviewFinding` record in `domain/` with severity, category, filePath, lineRange, message, confidence. `ReviewerOutcome.Completed` changes from `List<String>` to `List<ReviewFinding>`. Breaking change.
**Alternatives:**
- Keep `List<String>`, encode structured data as JSON strings — avoids breaking the sealed interface but pushes parsing burden downstream
- Parallel structured type alongside existing `List<String>` — more backward compatible but duplicative
**Rationale:** Pre-release platform — breaking changes cost nothing. Structured findings are required for trust scoring traceability. Raw strings cannot express severity, file location, or confidence.
**Trade-offs:** All existing callers of `ReviewerOutcome.Completed` must update. Stubs must return `ReviewFinding` instances instead of strings.
**Sources:** `review/src/main/java/.../ReviewerOutcome.java`, issue #203 acceptance criteria
**Exploration:** quick
**Status:** captured

## D3: Diff access — PrDiffService SPI

**Choice:** New port interface `PrDiffService` in `review/` module with `fetchDiff(repo, prNumber)` returning structured per-file diffs. GitHub implementation in `github/` module. Agents receive diff content without knowing about GitHub.
**Alternatives:**
- Enrich PrPayload with diff content — simpler but couples payload to content size and forces fetching for every review including DECLINE
- Agent fetches own diff via MCP server — most flexible but each agent pays API cost independently
**Rationale:** Clean domain boundary — agents don't know about GitHub. Diff fetching is centralised and cacheable. Per-file structure supports the chunking strategy (D8).
**Trade-offs:** New SPI to implement and maintain. GitHub module gains a new REST client dependency.
**Sources:** `github/src/main/java/.../GitHubPullRequestApi.java` (no existing diff method), `review/src/main/java/.../PrPayload.java`
**Exploration:** quick
**Status:** captured

## D4: CDI displacement — ReviewerAgentRegistry (REVISED)

**Choice:** `ReviewerAgentRegistry` collects all `ReviewerAgent` beans and indexes by `capability()`. Both stubs and LLM agents are plain `@ApplicationScoped` — no `@DefaultBean`. Registry resolves per-capability by `priority()`: stubs return 0, LLM agents return 1, highest priority wins.
**Alternatives:**
- CDI qualifier per capability — clean CDI but 6+ qualifier annotations and verbose injection
- `@Alternative @Priority` per pair — familiar pattern but manual priority management
- `@DefaultBean` on stubs — the established 1:1 displacement pattern, but incorrect for multi-instance ReviewerAgent (a single non-`@DefaultBean` LLM agent would suppress ALL 6 `@DefaultBean` stubs)
**Rationale:** The `@DefaultBean` displacement pattern (ARC42STORIES §9.4) is a 1:1 mechanism. With 6 stubs sharing the `ReviewerAgent` bean type, any single non-`@DefaultBean` agent would suppress all 6 defaults — breaking partial deployment. Priority-based registry resolution supports incremental LLM agent rollout.
**Trade-offs:** Registry is a new component to maintain. Must handle the case where no bean exists for a capability (log warning, skip).
**Sources:** devtown `@DefaultBean` displacement pattern (ARC42STORIES.MD §9.4), docs/protocols/casehub/alternative-extension-patterns.md
**Exploration:** quick
**Status:** captured (revised — original incorrectly used @DefaultBean; corrected during dimension reviews COH-R3-01, STR-R3-01, ROB-R2-01)

## D5: LLM invocation mode — one-shot invoke()

**Choice:** Use `AgentProvider.invoke(AgentSessionConfig)` for one-shot streaming. System prompt defines the reviewer role and output schema; user prompt contains the diff content.
**Alternatives:**
- `openSession()` multi-turn — can ask follow-up questions but 2-3x cost and complexity
- Configurable per capability — some get multi-turn, others one-shot
**Rationale:** Code review is naturally one-shot: here's the diff, give me findings. The AgentProvider streaming model collects TextDelta events into a complete response. Simpler, cheaper, sufficient.
**Trade-offs:** Can't do follow-up clarification on ambiguous findings. If a future capability needs multi-turn, the interface supports it but this implementation doesn't.
**Sources:** `casehub-platform/agent-api/.../AgentProvider.java`, `AgentSessionConfig.java`
**Exploration:** quick
**Status:** captured

## D6: Output parsing — JSON in system prompt

**Choice:** System prompt instructs the LLM to respond in a specific JSON schema matching `ReviewFinding` fields. Accumulate full text response from TextDelta events, parse with Jackson.
**Alternatives:**
- Tool-use structured output — more reliable schema adherence but requires MCP server setup
- Markdown with regex extraction — most natural for LLM but brittle and hard to validate
**Rationale:** Proven pattern across casehub-blocks (LlmContentSummariser, LlmDecomposition). Simple, reliable, no extra dependencies beyond Jackson (already present).
**Trade-offs:** LLM may occasionally produce invalid JSON — need graceful error handling (return Failed outcome).
**Sources:** casehub-blocks LlmContentSummariser, LlmDecomposition (both use this pattern)
**Exploration:** quick
**Status:** captured

## D7: Delivery scope — all 6 capabilities

**Choice:** Ship all 6 ReviewDomain capabilities: code-analysis, security-review, architecture-review, style-review, test-coverage, performance-analysis.
**Alternatives:**
- code-analysis only — validate pattern before replicating
- code-analysis + security-review — two high-value capabilities first
**Rationale:** Once infrastructure (PrDiffService, ReviewFinding, registry, base class, prompt parsing) is built, each additional capability is a system prompt + output mapping. Marginal cost per capability is low.
**Trade-offs:** Larger initial delivery. Each system prompt needs testing and tuning.
**Sources:** `domain/.../ReviewDomain.java` (6 constants), issue #203 deliverables
**Exploration:** quick
**Status:** captured

## D8: Diff strategy — per-file chunking

**Choice:** PrDiffService returns per-file diffs. LLM reviewers process one file at a time (or small batches), then aggregate findings. If a single file exceeds limits, truncate with a note.
**Alternatives:**
- Full diff with truncation — simple but loses context and can't scope findings to files
- Summary + targeted deep-dive — smarter but requires multi-turn (conflicts with D5)
**Rationale:** Stays within LLM context limits. Allows parallel per-file review. Each finding has a clear file scope. Natural fit with one-shot invocation (D5).
**Trade-offs:** Multiple LLM calls per PR (one per file or batch). Cross-file concerns may be missed — architecture-review mitigates by also receiving codeAnalysis context from the blackboard.
**Sources:** pr-review.yaml capability definitions (inputProjection shows what context each capability receives)
**Exploration:** quick
**Status:** captured

## D9: Model selection — agent-config.yaml manifest with aliases (REVISED)

**Choice:** Use casehub-platform's `agent-config.yaml` manifest with named aliases for per-capability model selection. Each capability references an alias (e.g. `"reasoning-heavy"` for security-review, `"cheap-fast"` for style-review) via `AgentProvider.invoke(config.withModel(alias))`. The manifest maps aliases to tiers/vendors/models per environment.
**Alternatives:**
- Hardcoded per capability — requires redeployment to change models (original D9, superseded)
- Tier refs directly (`tier:FLAGSHIP`, `tier:FAST`) — simpler but less configurable per-environment
- Single model for all — forces cost/quality trade-off across capabilities
**Rationale:** The platform now provides a hierarchical manifest system (`agent-config.yaml`) with four-step resolution (alias → tier → registry → backend key). Aliases decouple capability requirements from concrete model/vendor choices. Environment-specific manifests (dev, CI, production) resolve the same alias to different backends without code changes.
**Trade-offs:** Requires `agent-config.yaml` in devtown project root. Adds casehub-platform-agent-config dependency.
**Sources:** casehub-platform consumer-guide.md §Agent infrastructure, §Agent configuration manifest
**Exploration:** quick
**Status:** captured (revised from hardcoded)
**Revision reason:** Platform agent-config manifest was not known at original decision time. Manifest aliases provide the same per-capability flexibility without hardcoding.

## D10: Agent identity — full eidos integration

**Choice:** Register all 6 reviewer agents as eidos `AgentDescriptor`s via `META-INF/eidos/descriptors.yaml`. Each agent gets: structured identity (agentId, slot="reviewer"), capabilities with `modelRef` for model selection, and disposition for behavioural profile. Add `casehub-eidos-api` and `casehub-eidos` dependencies.
**Alternatives:**
- Minimal (agent-config only) — skip eidos, no structured identity or health probing
- Deferred (separate issue) — ship LLM reviewers without platform agent infrastructure
**Rationale:** Eidos is the platform's agent identity framework. Using it gives: per-capability `modelRef` (alias/tier) that flows through `AgentProvider`, `CapabilityHealth` probing at dispatch time (degraded, overloaded, epistemically weak), `SystemPromptRenderer` for identity portions of prompts, and a path to the knowledge graph for outcome tracking. This is the platform-coherent way to declare AI agents.
**Trade-offs:** Adds eidos dependency to devtown. Requires writing descriptors.yaml. Health probing integration with the engine's dispatch adds wiring.
**Depends on:** D9 (manifest aliases are referenced by modelRef on the descriptor)
**Sources:** casehub-eidos consumer-guide.md, AgentCapability.modelRef, CapabilityHealth SPI
**Exploration:** quick
**Status:** captured
