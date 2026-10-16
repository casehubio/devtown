## D1: Scope

**Choice:** Code + manifest JSON + setup guide
**Alternatives:**
- Code only — less onboarding friction but leaves App registration undocumented
- Code + manifest — no written guide for manual steps
**Rationale:** devtown will be installed on multiple orgs; a manifest enables one-click creation, and a guide covers manual steps and troubleshooting
**Trade-offs:** More deliverables but each is small
**Sources:** Issue #218 acceptance criteria
**Exploration:** quick
**Status:** captured

## D2: JWT library

**Choice:** nimbus-jose-jwt
**Alternatives:**
- java.security + Base64 — zero deps but hand-rolled JWT assembly to test and maintain
- smallrye-jwt — Quarkus-native but heavyweight (pulls MP JWT spec), designed for verification not signing
**Rationale:** Battle-tested, ~300KB, clean API for exactly this use case. One less thing to test vs hand-rolling.
**Trade-offs:** Adds one dependency
**Sources:** GitHub App auth docs (JWT signing requirement)
**Exploration:** quick
**Status:** captured

## D3: Auth wiring mechanism

**Choice:** ClientHeadersFactory with @RegisterClientHeaders on each interface
**Alternatives:**
- JAX-RS client request filter — more implicit, fewer annotations but less discoverable
- Config property (quarkus.rest-client.github-api.headers.Authorization) — static, can't rotate tokens
**Rationale:** MicroProfile standard, single CDI bean provides the header, explicit annotations on each interface
**Trade-offs:** 5 annotation additions (trivial)
**Sources:** MicroProfile REST Client spec; existing configKey="github-api" on all 5 interfaces
**Exploration:** quick
**Status:** captured

## D4: Token caching

**Choice:** Volatile CachedToken field with expiry check (single installation)
**Alternatives:**
- ConcurrentHashMap keyed by installation ID — multi-installation ready but ClientHeadersFactory has no request context to determine which installation a request targets (D3 incompatibility found in decision review)
- Add Caffeine dependency — overkill for single-token caching, not on devtown's classpath
**Rationale:** ClientHeadersFactory.update() receives only HTTP headers, no URI or path params — it cannot key into a multi-installation cache. Single installation with volatile field is the honest design for the chosen wiring mechanism.
**Trade-offs:** Multi-installation would require changing both caching (D4) and wiring (D3) mechanisms
**Sources:** GitHub installation token TTL (1 hour); decision review round 1 finding (D3+D4 incompatibility)
**Exploration:** quick
**Status:** revised (decision review round 1)

## D5: Module placement

**Choice:** All auth code in github/ module
**Alternatives:**
- Split github/ + app/ — follows three-tier rule strictly but spreads GitHub logic across modules
- app/ only — simpler CDI but GitHub auth logic divorced from GitHub REST clients
**Rationale:** Keeps all GitHub concerns together. Config injection works via Jandex (github module already indexed).
**Trade-offs:** None significant — github/ already has CDI annotations on all its beans
**Sources:** Existing github/pom.xml (has jandex-maven-plugin); three-tier rule in CLAUDE.md
**Exploration:** quick
**Status:** captured

## D6: Config-driven provider selection via @Produces factory

**Choice:** Single @Produces factory method that checks config and creates the right GitHubTokenProvider implementation
**Alternatives:**
- @DefaultBean displacement (PatTokenProvider @DefaultBean, AppTokenProvider @ApplicationScoped) — @ApplicationScoped always wins over @DefaultBean in the same module, making PatTokenProvider permanently dead code
- Profile-based activation (@IfBuildProfile) — ties auth mode to Quarkus profile rather than config presence
- Config-driven switch in one class — violates SRP, harder to test
**Rationale:** @Produces factory checks Optional config properties: all three App props present → AppTokenProvider; none → PatTokenProvider. Explicit, no CDI displacement ambiguity, both implementations independently testable without CDI.
**Trade-offs:** Factory class is additional boilerplate, but eliminates the CDI displacement footgun
**Sources:** Decision review round 1 finding (D6 CDI displacement flaw); cdi-classpath-presence-requires-module-separation protocol
**Exploration:** quick
**Status:** captured

## D7: Private key loading

**Choice:** File path config property (devtown.github.app.private-key-path)
**Alternatives:**
- Inline config property (full PEM in env var) — multi-line env vars fragile across shells and CI
- Both with file-first precedence — two code paths to test for marginal flexibility
**Rationale:** File path is the production standard for secrets. Kubernetes secret volumes map directly. Dev mode points to a local file.
**Trade-offs:** Requires a file on disk (not just an env var)
**Sources:** Kubernetes secrets best practices; GitHub App private key format (PEM)
**Exploration:** quick
**Status:** captured

## D8: ClientHeadersFactory registration style

**Choice:** Explicit @RegisterClientHeaders annotation on each of the 5 interfaces
**Alternatives:**
- Global default via quarkus.rest-client.github-api.providers in application.properties — invisible wiring, only works for shared configKey
**Rationale:** Explicit, grep-able, self-documenting. When reading any interface you can see auth is wired.
**Trade-offs:** 5 one-line annotation additions (trivial)
**Depends on:** D3 (ClientHeadersFactory chosen as mechanism)
**Sources:** MicroProfile REST Client @RegisterClientHeaders spec
**Exploration:** quick
**Status:** captured
