# GitHub App Authentication — Design Spec

**Issue:** casehubio/devtown#218
**Branch:** issue-218-github-app-auth
**Date:** 2026-10-15

## Summary

Replace the unused PAT config property with a fully wired GitHub App authentication
system. All 5 REST client interfaces gain an `Authorization: Bearer <token>` header
via a shared `ClientHeadersFactory`. In production, tokens come from GitHub App
installation token exchange (JWT → installation access token, cached with volatile
field). In dev mode, a PAT fallback activates via a `@Produces` factory that checks
config presence.

Deliverables: Quarkus auth code, GitHub App Manifest JSON for one-click registration,
and a setup guide.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  github/ module                                           │
│                                                           │
│  GitHubTokenProviderProducer (@Produces factory)          │
│    ├─ App config present → AppTokenProvider               │
│    │     ├─ GitHubAppJwtGenerator (App ID + PEM → JWT)    │
│    │     └─ volatile CachedToken (single installation)    │
│    │         └─ POST /app/installations/{id}/access_tokens│
│    │            via InstallationTokenClient (REST client)  │
│    └─ App config absent → PatTokenProvider                │
│          └─ reads devtown.github.token                    │
│                                                           │
│  GitHubAuthHeadersFactory (ClientHeadersFactory)          │
│    └─ injects GitHubTokenProvider                         │
│    └─ returns Authorization: Bearer <token>               │
│                                                           │
│  @RegisterClientHeaders(GitHubAuthHeadersFactory.class)   │
│  on: GitHubChecksApi, GitHubPullRequestApi,               │
│      GitHubMergeApi, GitHubRepoApi, GitHubGitApi          │
└──────────────────────────────────────────────────────────┘
```

## Components

### GitHubTokenProvider (interface)

```java
public interface GitHubTokenProvider {
    String getToken();
}
```

Single method, returns the bearer token string. Implementations decide where
the token comes from.

### GitHubTokenProviderProducer

CDI `@Produces` factory that checks config and creates the right implementation:

```java
@ApplicationScoped
public class GitHubTokenProviderProducer {

    @Produces @ApplicationScoped
    GitHubTokenProvider tokenProvider(
            @ConfigProperty(name = "devtown.github.app.id") Optional<String> appId,
            @ConfigProperty(name = "devtown.github.app.private-key-path") Optional<String> keyPath,
            @ConfigProperty(name = "devtown.github.app.installation-id") Optional<String> installationId,
            @ConfigProperty(name = "devtown.github.token") Optional<String> pat,
            @RestClient InstallationTokenClient tokenClient) {
        if (appId.isPresent() && keyPath.isPresent() && installationId.isPresent()) {
            return new AppTokenProvider(appId.get(), keyPath.get(),
                installationId.get(), tokenClient);
        }
        if (appId.isPresent() || keyPath.isPresent() || installationId.isPresent()) {
            throw new ConfigurationException("Partial GitHub App config: all three of " +
                "app.id, app.private-key-path, app.installation-id must be set, or none");
        }
        return new PatTokenProvider(pat.orElse(""));
    }
}
```

Partial config (e.g. `app.id` set but no `private-key-path`) throws a clear
startup error. No CDI displacement needed — the factory is the single source.

### PatTokenProvider

- Plain class (no CDI annotations — created by the producer)
- Returns the PAT value directly (or empty string if not configured)
- Activated when no App config properties are present

### AppTokenProvider

- Plain class (no CDI annotations — created by the producer)
- Constructed with App ID, private key path, installation ID, and `InstallationTokenClient`
- Loads the RSA private key from file at construction time (fail-fast)
- Uses `GitHubAppJwtGenerator` to create a short-lived JWT (10 min TTL)
- Exchanges the JWT for an installation access token via `InstallationTokenClient`
- Caches the token in a `volatile CachedToken` field (single installation)
- Refreshes when the cached token is within 5 minutes of expiry
- Thread-safe: `synchronized` block around refresh (low contention — refreshes once per ~55 minutes)

**CachedToken record:**
```java
record CachedToken(String token, Instant expiresAt) {
    boolean isExpiringSoon(Instant now) {
        return now.isAfter(expiresAt.minus(Duration.ofMinutes(5)));
    }
}
```

### GitHubAppJwtGenerator

Pure utility class (no CDI, static method or injected as a bean):
- Input: App ID (String), RSA private key (PrivateKey)
- Output: Signed JWT string (RS256, 10-minute expiry)
- Uses nimbus-jose-jwt (`RSASSASigner`, `JWSHeader`, `JWTClaimsSet`)

JWT claims per GitHub spec:
- `iss`: App ID
- `iat`: now - 60 seconds (clock skew tolerance)
- `exp`: now + 10 minutes (maximum allowed by GitHub)

Private key loading:
- Reads PEM file from `devtown.github.app.private-key-path`
- Strips `-----BEGIN/END RSA PRIVATE KEY-----` markers
- Base64-decodes to `PKCS8EncodedKeySpec`
- Generates `RSAPrivateKey` via `KeyFactory.getInstance("RSA")`

### InstallationTokenClient

A dedicated MicroProfile REST client for the App-level GitHub API:

```java
@RegisterRestClient(configKey = "github-app-api")
@Path("/app")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface InstallationTokenClient {

    @POST
    @Path("/installations/{installation_id}/access_tokens")
    InstallationToken createInstallationToken(
        @PathParam("installation_id") String installationId,
        @HeaderParam("Authorization") String jwtAuth);
}
```

Note: This client uses `@HeaderParam` directly (not `ClientHeadersFactory`)
because it authenticates with the JWT, not the installation token. It has its
own `configKey = "github-app-api"` pointing at the same base URL.

**InstallationToken record:**
```java
public record InstallationToken(String token, Instant expiresAt) {}
```

Jackson deserialization from GitHub's response:
```json
{
  "token": "ghs_...",
  "expires_at": "2026-10-15T11:00:00Z"
}
```

### GitHubAuthHeadersFactory

```java
@ApplicationScoped
public class GitHubAuthHeadersFactory implements ClientHeadersFactory {

    private final GitHubTokenProvider tokenProvider;

    @Inject
    public GitHubAuthHeadersFactory(GitHubTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        String token = tokenProvider.getToken();
        if (token != null && !token.isEmpty()) {
            clientOutgoingHeaders.putSingle("Authorization", "Bearer " + token);
        }
        clientOutgoingHeaders.putSingle("Accept", "application/vnd.github+json");
        clientOutgoingHeaders.putSingle("X-GitHub-Api-Version", "2022-11-28");
        return clientOutgoingHeaders;
    }
}
```

Also adds the `Accept` and `X-GitHub-Api-Version` headers that GitHub recommends
on all API calls.

### REST Client Interface Changes

Each of the 5 interfaces gets one annotation added:

```java
@RegisterRestClient(configKey = "github-api")
@RegisterClientHeaders(GitHubAuthHeadersFactory.class)  // ← added
@Path("/repos")
public interface GitHubChecksApi { ... }
```

Same for: `GitHubPullRequestApi`, `GitHubMergeApi`, `GitHubRepoApi`, `GitHubGitApi`.

## Configuration

### application.properties

```properties
# ── GitHub App authentication ─────────────────────────────────────────────────
devtown.github.app.id=${GITHUB_APP_ID:}
devtown.github.app.private-key-path=${GITHUB_APP_PRIVATE_KEY_PATH:}
devtown.github.app.installation-id=${GITHUB_INSTALLATION_ID:}

# PAT fallback (dev mode / testing)
devtown.github.token=${GITHUB_TOKEN:}

# Installation token REST client (same base URL as github-api)
quarkus.rest-client.github-app-api.url=https://api.github.com
```

### Dev profile overrides

No dev-specific overrides needed — when `GITHUB_APP_ID` is unset, the provider
falls back to PAT mode using `devtown.github.token`.

## GitHub App Manifest

File: `github/src/main/resources/github-app-manifest.json`

```json
{
  "name": "devtown",
  "url": "https://github.com/casehubio/devtown",
  "hook_attributes": {
    "url": "https://<your-domain>/api/github/webhook",
    "active": true
  },
  "redirect_url": "https://<your-domain>/api/github/app/callback",
  "public": false,
  "default_permissions": {
    "pull_requests": "write",
    "checks": "write",
    "contents": "read",
    "metadata": "read"
  },
  "default_events": [
    "pull_request",
    "pull_request_review",
    "check_suite",
    "check_run"
  ]
}
```

Placeholders (`<your-domain>`) must be replaced during registration.

## Setup Guide

File: `docs/guides/github-app-setup.md`

Covers:
1. One-click creation via manifest (link to GitHub manifest flow URL)
2. Manual creation steps (for orgs that restrict manifest flow)
3. Generating and saving the private key
4. Installing the App on a GitHub org/repo
5. Finding the installation ID
6. Configuring devtown environment variables
7. Verifying the integration (expected log output, test API call)

## Testing Strategy

### Unit tests (github/ module)

| Class | What to test |
|-------|-------------|
| `GitHubAppJwtGenerator` | Correct JWT structure (header, claims, signature verifiable with public key), clock skew tolerance, expiry |
| `AppTokenProvider` | Cache hit (returns cached token), cache miss (calls InstallationTokenClient), expiry refresh (token near expiry triggers refresh via volatile field + synchronized) |
| `PatTokenProvider` | Returns configured PAT, returns empty when unconfigured |
| `GitHubAuthHeadersFactory` | Sets Authorization header when token present, omits when empty, sets Accept and API version headers |

### Integration tests (app/ module, @QuarkusTest)

| Test | What to verify |
|------|---------------|
| Producer with App config | `GitHubTokenProviderProducer` creates `AppTokenProvider` when all three App properties set |
| Producer with no App config | Producer creates `PatTokenProvider` when App properties absent |
| Producer with partial config | Producer throws `ConfigurationException` on partial App config |

### No end-to-end GitHub API tests

Real GitHub App token exchange requires a registered App and valid private key.
This is verified manually via the setup guide's verification step, not automated.
Mock the `InstallationTokenClient` in unit tests.

## Dependencies

### New (github/pom.xml)

```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
</dependency>
```

Version managed by Quarkus BOM (if present) or declared in parent pom.xml
`<dependencyManagement>`.

### Existing (no changes)

- `quarkus-rest-client` — already present
- `quarkus-rest-client-jackson` — already present

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Partial App config (e.g. app-id without private-key-path) | Startup error with clear message listing which properties are missing |
| Invalid private key file (wrong format, not found) | Startup error — fail fast, don't silently fall back to PAT |
| JWT exchange fails (GitHub API error) | Log error, throw RuntimeException — request fails, retry on next call |
| Installation token expired during request | Token refresh is proactive (5 min before expiry), but if a request races with refresh, GitHub returns 401 → the factory does not retry (caller handles) |
| PAT mode with empty token | Requests go out unauthenticated (rate-limited to 60/hr) — acceptable for dev mode with NoOp clients |

## Files Changed

### New files
- `github/.../GitHubTokenProvider.java` — interface
- `github/.../GitHubTokenProviderProducer.java` — @Produces factory
- `github/.../PatTokenProvider.java` — PAT fallback (plain class)
- `github/.../AppTokenProvider.java` — App auth + volatile cache (plain class)
- `github/.../GitHubAppJwtGenerator.java` — JWT assembly
- `github/.../InstallationTokenClient.java` — REST client for token exchange
- `github/.../InstallationToken.java` — response record
- `github/.../GitHubAuthHeadersFactory.java` — ClientHeadersFactory
- `github/src/main/resources/github-app-manifest.json` — App Manifest
- `docs/guides/github-app-setup.md` — setup guide
- Tests for all new classes

### Modified files
- `github/.../GitHubChecksApi.java` — add `@RegisterClientHeaders`
- `github/.../GitHubPullRequestApi.java` — add `@RegisterClientHeaders`
- `github/.../GitHubMergeApi.java` — add `@RegisterClientHeaders`
- `github/.../GitHubRepoApi.java` — add `@RegisterClientHeaders`
- `github/.../GitHubGitApi.java` — add `@RegisterClientHeaders`
- `github/pom.xml` — add nimbus-jose-jwt dependency
- `app/src/main/resources/application.properties` — add App config properties

## Out of Scope

- Multi-org routing (single installation ID for now; multi-installation would
  require changing both caching and wiring mechanisms — ClientHeadersFactory has
  no request context to determine which installation a request targets)
- Token permission scoping per request (all requests use the installation's full
  permission set)
- GitHub App webhook delivery verification changes (existing `GitHubSignatureVerifier`
  works identically with App webhooks)
- Automatic App installation flow (user installs manually or via manifest)

## References

- Issue #218 — GitHub App setup and authentication
- `github/src/main/java/io/casehub/devtown/github/GitHubChecksApi.java` — existing REST client pattern
- `app/src/main/resources/application.properties:52-55` — existing GitHub config
- `ARC42STORIES.MD §9.4` — @DefaultBean displacement pattern documentation
- MicroProfile REST Client spec — ClientHeadersFactory, @RegisterClientHeaders
- GitHub Docs — Authenticating as a GitHub App (JWT → installation token flow)
- GitHub Docs — Creating a GitHub App from a manifest
- nimbus-jose-jwt — RSASSASigner for RS256 JWT signing
