# Design: allowedTypes Enforcement on Normative Channels

**Issue:** devtown#54  
**Date:** 2026-06-03  
**Status:** draft

---

## Context and Issue Correction

Issue #54 was filed with two incorrect premises:

1. "ChannelService.create() does not expose allowedTypes" — FALSE. A 9-arg overload
   exists: `create(name, description, semantic, barrierContributors, allowedWriters,
   adminInstances, rateLimitPerChannel, rateLimitPerInstance, allowedTypes)`.

2. "enforcement is a Claudony NormativeChannelLayout SPI concern" — FALSE.
   `NormativeChannelLayout` does not exist in any JAR. Enforcement is already live via
   `StoredMessageTypePolicy.validate()` in `casehub-qhorus`, which throws
   `MessageTypeViolationException` if a dispatched type is not in the channel's
   `allowedTypes` set.

The real issue: devtown's `findOrCreate` uses the 4-arg overload and leaves `allowedTypes`
null (permissive). The API and enforcement infrastructure exist; devtown just hasn't used them.

Additional constraint: `allowedTypes` is immutable once set — `ChannelService` has no
`setAllowedTypes` method. Getting the type contracts right at creation time is critical
(see also: §Migration).

---

## Foundation Bug: deniedTypes Is Documented but Not Enforced

`Channel.deniedTypes` has a javadoc that reads: _"If a type appears in both allowedTypes
and deniedTypes, denial wins."_ And: _"If a new MessageType is added to Qhorus with no
commitment effect (like EVENT), add it here for all governance channels — this is the
mechanical anchor for that obligation."_

`StoredMessageTypePolicy.validate()` reads `channel.allowedTypes` only — it does not check
`deniedTypes`. The "denial wins" promise is unimplemented.

This is a pre-existing qhorus bug. It does not block this fix — the `allowedTypes`
whitelist approach is the correct enforcement mechanism regardless. But it means:

- Any caller using `deniedTypes` today gets no enforcement — silently ignored
- The `deniedTypes` javadoc describes the correct governance pattern for excluding EVENT
  from commitment channels; that pattern cannot be relied on until the bug is fixed

**Action:** file `casehubio/qhorus` issue: _`StoredMessageTypePolicy` ignores `deniedTypes`
— document says "denial wins", implementation doesn't check it._

---

## Channel Type Contracts

Three normative channels are created per PR review. The immutability of `allowedTypes`
requires forward-looking type selection — any type that could legitimately appear in
Layer 6+ must be included now, or require a channel recreation.

### MessageType reasoning for /work

All nine `MessageType` values considered:

| Type | Decision | Reasoning |
|------|----------|-----------|
| COMMAND | ✅ include | Orchestrator sends to agents |
| STATUS | ✅ include | **Deadline extension** — real out-of-process agents doing security/architecture reviews (minutes, not ms) send STATUS to extend commitment windows. Same immutability argument as FAILURE. |
| DONE | ✅ include | Commitment fulfilled |
| DECLINE | ✅ include | Formal scope boundary |
| FAILURE | ✅ include | Commitment technical failure — Layer 6 real agents will use this |
| HANDOFF | ❌ exclude | Specialist-to-specialist delegation bypasses the orchestrator. The devtown model is orchestrator-mediated: if reassignment is needed, the agent sends DECLINE and the orchestrator re-routes. HANDOFF on `/work` would create peer-to-peer coupling that contradicts the ACM blackboard model. |
| QUERY | ❌ exclude | Interrogative pattern has no commitment effect on `/work` |
| RESPONSE | ❌ exclude | Answers QUERY — same reasoning |
| EVENT | ❌ exclude | Observation only; `/observe` is the telemetry channel |

### Final type table

| Channel | `allowedTypes` | Rationale |
|---------|----------------|-----------|
| `pr-review-{n}/work` | `COMMAND,STATUS,DONE,DECLINE,FAILURE` | Full commitment lifecycle including deadline extension |
| `pr-review-{n}/observe` | `EVENT` | Telemetry/audit only. **Forward assumption:** this constraint is set before Layer 4 is designed. EVENT is structurally correct (it bypasses `allowedWriters` and rate limiting — appropriate for audit events). If Layer 4 requires a different type on `/observe`, the `setAllowedTypes` qhorus issue (see §Architecture) is the escape hatch. |
| `pr-review-{n}/oversight` | `COMMAND,DONE,DECLINE` | Human oversight decisions. FAILURE excluded: human decisions are DONE or DECLINE; FAILURE on an oversight channel is a bug. STATUS excluded: oversight is a terminal gate, not an async long-running operation. |

### /oversight writer gap

`allowedTypes` narrows _what_ can be posted; it does not narrow _who_ can post it.
`allowedWriters = null` on `/oversight` means any actor (including a misbehaving agent)
can post DONE and close an oversight gate. The correct fix is to set `allowedWriters` to
the human oversight actor identity — but that identity is not yet defined in devtown's
design. This is explicitly deferred to the layer that actively uses the oversight channel
(Layer 4 or wherever HITL wiring is added). **A GitHub issue should track this.**

---

## Implementation

### Typed constants (not raw strings)

`StoredMessageTypePolicy` parses `allowedTypes` via `MessageType::valueOf`. A typo compiles
but throws `IllegalArgumentException` at dispatch time — not caught by callers expecting
`MessageTypeViolationException`. Use typed constants built from `EnumSet`:

```java
import static io.casehub.qhorus.api.message.MessageType.*;
import java.util.EnumSet;
import java.util.stream.Collectors;

private static final String WORK_ALLOWED_TYPES =
    EnumSet.of(COMMAND, STATUS, DONE, DECLINE, FAILURE)
           .stream().map(Enum::name).collect(Collectors.joining(","));

private static final String OBSERVE_ALLOWED_TYPES =
    EnumSet.of(EVENT)
           .stream().map(Enum::name).collect(Collectors.joining(","));

private static final String OVERSIGHT_ALLOWED_TYPES =
    EnumSet.of(COMMAND, DONE, DECLINE)
           .stream().map(Enum::name).collect(Collectors.joining(","));
```

### Named channel factory methods

Replace the single generic `findOrCreate(String name)` with three named methods,
each declaring its type contract:

```java
private Channel findOrCreateWorkChannel(String prefix) {
    String name = prefix + "/work";
    return channelService.findByName(name)
            .map(ch -> requireAllowedTypes(ch, WORK_ALLOWED_TYPES))
            .orElseGet(() -> channelService.create(
                    name, null, ChannelSemantic.APPEND, ORCHESTRATOR,
                    null, null, null, null, WORK_ALLOWED_TYPES));
}

private Channel findOrCreateObserveChannel(String prefix) {
    String name = prefix + "/observe";
    return channelService.findByName(name)
            .map(ch -> requireAllowedTypes(ch, OBSERVE_ALLOWED_TYPES))
            .orElseGet(() -> channelService.create(
                    name, null, ChannelSemantic.APPEND, ORCHESTRATOR,
                    null, null, null, null, OBSERVE_ALLOWED_TYPES));
}

private Channel findOrCreateOversightChannel(String prefix) {
    String name = prefix + "/oversight";
    return channelService.findByName(name)
            .map(ch -> requireAllowedTypes(ch, OVERSIGHT_ALLOWED_TYPES))
            .orElseGet(() -> channelService.create(
                    name, null, ChannelSemantic.APPEND, ORCHESTRATOR,
                    null, null, null, null, OVERSIGHT_ALLOWED_TYPES));
}
```

---

## Migration

`allowedTypes` is immutable and `ChannelService` has no `setAllowedTypes` method. The
`findOrCreate` pattern means existing channels (created before this fix, with
`allowedTypes = null`) would be returned unchanged — silently operating without enforcement.

**Fix:** the `requireAllowedTypes` guard (shown above) validates the returned channel after
`findByName`. If the found channel's `allowedTypes` does not match the expected value, it
throws `IllegalStateException` — fail fast rather than silently operate with weaker
enforcement:

```java
private Channel requireAllowedTypes(Channel ch, String expected) {
    if (!expected.equals(ch.allowedTypes)) {
        throw new IllegalStateException(
            "Channel '" + ch.name + "' has allowedTypes='" + ch.allowedTypes
            + "' but expected '" + expected + "'. "
            + "Delete and recreate, or wait for qhorus setAllowedTypes (qhorus#N).");
    }
    return ch;
}
```

In practice, devtown's channels are named per-PR (`pr-review-{prNumber}/work`) so
pre-existing permissive channels only exist if the same PR number was previously reviewed.
In tests, PR numbers are unique per test. In production, the same PR could be re-reviewed.

**Long-term fix:** qhorus needs `setAllowedTypes(String channelName, String types)` so
existing channels can be migrated without deletion. File as `casehubio/qhorus` enhancement.

---

## Test Coverage

`PrReviewQhorusLifecycleTest` additions:

**Negative (rejection):**
- `/work` rejects `EVENT` → `MessageTypeViolationException`
- `/observe` rejects `COMMAND` → `MessageTypeViolationException`
- `/oversight` rejects `FAILURE` → `MessageTypeViolationException`
- `/oversight` rejects `EVENT` → `MessageTypeViolationException`
- `/work` accepts `STATUS` → dispatches successfully (deadline extension confirmed)
- `/work` accepts `FAILURE` → dispatches successfully (Layer 6 forward-compatibility confirmed)

**Positive (acceptance):**
- `/observe` accepts `EVENT` → dispatches successfully
- `/oversight` accepts `COMMAND` → dispatches successfully
- `/oversight` accepts `DONE` → dispatches successfully

**Migration guard:**
- Channel already exists with `allowedTypes = null` → `requireAllowedTypes` throws
  `IllegalStateException` rather than silently returning the permissive channel

---

## Architecture: qhorus Issues to File

Before implementation closes, file these three qhorus issues:

| Issue | Type | Description |
|-------|------|-------------|
| `deniedTypes` not enforced | Bug | `StoredMessageTypePolicy` ignores `Channel.deniedTypes`; documented "denial wins" semantics are silent no-op |
| `setAllowedTypes` API | Enhancement | `ChannelService` needs `setAllowedTypes(String name, String types)` to enable migration of permissive channels without deletion |
| `Set<MessageType>` overload | Enhancement | `ChannelService.create()` should accept `Set<MessageType>` — compile-time safety vs raw string parsed at dispatch. Comma-string reduced to a persistence detail. |

---

## Out of Scope

- `allowedWriters` on `/oversight` — deferred to when oversight channel is actively used; a GitHub issue tracks it
- `NormativeChannelLayout` SPI — not a real concept; do not reference it further
- Changes to `/observe` Layer 4 content — the `EVENT` constraint is a forward assumption; Layer 4 spec will validate it
- Race condition on concurrent `findOrCreate` for same PR number — pre-existing pattern documented in GE-20260529-88b7b6, not introduced by this fix, out of scope

---

## Document Updates

After implementation:
- Close devtown#54 with a note correcting the original framing
- Update `docs/specs/2026-05-29-layer3-qhorus-messaging-design.md` §allowedTypes to
  reflect actual state (constraints now enforced; NormativeChannelLayout was incorrect)
- Update `LAYER-LOG.md` Layer 3 entry — remove NormativeChannelLayout reference, record fix
