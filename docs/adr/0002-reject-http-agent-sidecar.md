# ADR-0002: Reject HTTP Agent Sidecar for Claudony Invocation

**Status:** Rejected
**Date:** 2026-11-01
**Issue:** casehubio/devtown#295

## Context

Platform #666 shipped `agent-http` and `agent-http-core` modules — a structured HTTP endpoint (`AgentHttpService`) wrapping `AgentProvider.invoke()`. The question was whether devtown should adopt this for AI agent invocation instead of the current in-JVM path.

## Decision

Rejected. Devtown continues using direct `AgentProvider` invocation via `StructuredAgentInvoker`.

## Rationale

The `agent-http` module adds HTTP serialization on top of the same `AgentProvider.invoke()` call devtown already makes in-JVM. The current path is `LlmAgentBase` → `StructuredAgentInvoker.invokeRaw()` → `AgentProvider` + `AgentSessionConfig`. The HTTP endpoint exists for external integrations and functional testing of deployed agents — not for in-process use by domain applications.

Adopting it would add network latency and serialization cost for zero functional gain. The only scenario where `agent-http` would matter is if reviewer agents ran out-of-process (separate JVM/container). That is a deployment topology decision, not an SPI wiring decision, and nothing in the current architecture requires it.

## Consequences

- Devtown reviewer agents remain in-JVM, invoked through `AgentProvider` directly
- If a future deployment topology requires out-of-process agents, this decision should be revisited
- The `agent-http` module remains available for external integration testing
