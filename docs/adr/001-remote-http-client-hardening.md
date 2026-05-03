# 1. Remote HTTP client hardening: body limits, shutdown timeouts and HTTP version

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

The remote provider in `openflags-provider-remote` ships an HTTP client used by
`RemoteFlagProvider` to fetch flag definitions from a configured backend. The
2026-05-03 architecture review surfaced three concrete defects in this client
that, taken together, allow a misbehaving or hostile server to degrade the host
application:

- **A-03 — shutdown timeout vs request timeout**: `RemoteFlagProvider.shutdown()`
  awaits executor termination for a hard-coded `5s` and only then closes the
  underlying `HttpClient`. The default `requestTimeout` is `10s`, so an in-flight
  request can outlive the await window: the executor reports "not terminated",
  the client is closed underneath the still-running request, and the shutdown
  log emits a misleading warning. Worse, the close-after-await ordering means
  the request is never actively cancelled — it just races the JVM exit.
- **A-06 — HTTP version forced to HTTP/2**: the client builder pins
  `HttpClient.Version.HTTP_2`. Against a plain `http://` endpoint without h2c
  upgrade support (a common case in dev environments and some corporate proxies)
  the JDK client falls back silently in some JDKs and fails outright in others,
  producing opaque `IOException`s. There is no escape hatch for operators that
  need HTTP/1.1.
- **M-05 — unbounded response body**: the client uses `BodyHandlers.ofString()`,
  which buffers the full response in memory with no upper bound. A server that
  responds with a multi-gigabyte body (accidentally or maliciously) will OOM the
  JVM hosting `RemoteFlagProvider` before any timeout fires.

These three issues live in a single component (`RemoteHttpClient` plus its
configuration surface in `RemoteProviderConfig`) and are best addressed in one
change so the public configuration record only grows once.

The relevant complication is **source compatibility of `RemoteProviderConfig`**.
The canonical record currently has 11 components. Any new field added to a Java
record breaks every call site that uses the canonical constructor positionally.
The known internal call site is `OpenFlagsAutoConfiguration` (hybrid mode),
which constructs the config with the 11-arg form. External users that pinned
`RemoteProviderConfig` directly would also break. The original review claim
that this could be done "non-breaking" was wrong unless an explicit overload is
added.

References to specific line ranges follow the layout at the time of the review;
exact line numbers will shift with the implementation PR.

## Decision

Apply the following four changes together as part of PR 7.

### 1. Configurable response body limit (`maxResponseBytes`)

Add a `long maxResponseBytes` field to `RemoteProviderConfig` with a default
of **10 MiB** (`10L * 1024 * 1024`). The HTTP client switches from
`BodyHandlers.ofString()` to a bounded body subscriber that aborts the response
with a dedicated `ResponseTooLargeException` once the cumulative byte count
exceeds the configured limit.

**Why 10 MiB**: realistic flag payloads (even with a few thousand flags and
verbose JSON) sit comfortably under 1 MiB. 10 MiB leaves an order of magnitude
of headroom for misuse without giving an attacker a free OOM. The value is a
default, not a cap — operators with legitimate large payloads can raise it.

**Why a hard abort instead of streaming truncation**: a truncated payload would
deserialize to garbage and produce confusing downstream errors. An explicit
exception lets the existing failure-handling path (backoff, metrics, listener)
treat oversized responses as a poll failure.

### 2. Configurable HTTP version with `AUTO` heuristic

Introduce a public enum `com.openflags.remote.HttpVersion` with three values:

- `AUTO` — derive the version from the request scheme: `https://` → HTTP/2,
  `http://` → HTTP/1.1.
- `HTTP_1_1` — force HTTP/1.1.
- `HTTP_2` — force HTTP/2 (current behaviour).

Add `HttpVersion httpVersion` to `RemoteProviderConfig`, defaulting to `AUTO`.
`RemoteHttpClient` selects the JDK `HttpClient.Version` per request based on
this field.

**Why an enum and not a `boolean http2Enabled`**: a boolean cannot express the
heuristic "follow the scheme" cleanly, and a future HTTP/3 option would force a
breaking change. The enum costs three identifiers and absorbs that future
extension.

**Why `AUTO` as default and not `HTTP_1_1`**: the vast majority of production
deployments use TLS, where HTTP/2 is the right choice and is what the current
code already does. `AUTO` preserves that for `https://` while fixing the
`http://` failure mode without operator intervention.

### 3. Shutdown order and derived await timeout

Rewrite `RemoteFlagProvider.shutdown()` so that:

1. The scheduled poller executor is shut down (`shutdown()` first, then
   `shutdownNow()` if the await expires).
2. The await window is computed at runtime as
   `Math.max(5_000L, requestTimeout.toMillis() + connectTimeout.toMillis())`
   milliseconds, not a hard-coded 5 seconds.
3. **After** awaiting termination (or timing out), `httpClient.close()` is
   invoked. The JDK `HttpClient.close()` (Java 21+) cancels in-flight exchanges
   cleanly, which is the desired behaviour for the timeout path.

**Why `max(5s, request + connect)`**: a poll in flight at shutdown time can
legitimately take up to `connectTimeout + requestTimeout`. Awaiting less than
that guarantees a spurious "did not terminate" warning on every clean shutdown
that happens during a poll. The 5-second floor preserves the existing minimum
for configurations that set both timeouts very low.

**Why close the client last**: closing first and then awaiting means the
in-flight request gets its socket pulled out from underneath, surfacing as an
`IOException` that the poller logs as a failure. Closing last lets the request
either finish naturally or hit its own timeout, which is observed correctly by
the poller's existing error path.

### 4. Source-compatible 11-arg constructor overload

The canonical record grows from 11 to 13 components. To keep the existing
11-arg call sites compiling unchanged, add an explicit constructor overload on
`RemoteProviderConfig` that delegates to the canonical constructor with default
values for `maxResponseBytes` and `httpVersion`:

```java
public RemoteProviderConfig(
        URI baseUrl,
        String flagsPath,
        String authHeaderName,
        String authHeaderValue,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration pollInterval,
        Duration cacheTtl,
        String userAgent,
        int failureThreshold,
        Duration maxBackoff) {
    this(
            baseUrl,
            flagsPath,
            authHeaderName,
            authHeaderValue,
            connectTimeout,
            requestTimeout,
            pollInterval,
            cacheTtl,
            userAgent,
            failureThreshold,
            maxBackoff,
            DEFAULT_MAX_RESPONSE_BYTES,
            HttpVersion.AUTO);
}
```

**Why an overload and not just defaults**: Java records do not support default
values for components. Without this overload, every existing positional call to
`new RemoteProviderConfig(...)` with 11 arguments is a compile error. The
overload is the minimum invasive way to keep the public surface of 1.x source
compatible.

**Deprecation policy of the overload**: open. The maintainer may either leave
it un-annotated (it is a perfectly valid way to opt into defaults) or mark it
`@Deprecated` to nudge users toward an explicit builder API in a future release.
This ADR does not pre-commit to either choice.

### Companion changes

- `OpenFlagsProperties.RemoteProperties` exposes `maxResponseBytes` and
  `httpVersion` so Spring Boot users can configure them via
  `application.yaml`.
- The hybrid call site at `OpenFlagsAutoConfiguration` (around lines 158-169 at
  review time) is **not** required to change in PR 7 thanks to the overload.
  Migrating it to pass the new fields explicitly is a follow-up. See PR 7 in
  `05-plan-ejecucion.md` for the exact scope.

## Consequences

### Positive

- A misbehaving server cannot OOM the host application via an unbounded
  response body.
- `http://` deployments work out of the box without an h2c-capable backend.
- Clean shutdown during an in-flight poll no longer logs a spurious "did not
  terminate" warning, and the in-flight request is cancelled deterministically.
- Operators get two new knobs (`maxResponseBytes`, `httpVersion`) with safe
  defaults; no action is required to benefit from the fix.
- Source compatibility is preserved: existing 11-arg construction keeps
  compiling.

### Negative / Risks

- The `HttpVersion` enum adds public API surface that must be maintained going
  forward. Adding HTTP/3 later is straightforward, but renaming or removing a
  value is breaking.
- The 10 MiB default is a guess. Deployments with unusually large flag
  catalogs may need to raise it explicitly and will see `ResponseTooLargeException`
  until they do. This is documented in the release notes for PR 7.
- The derived shutdown timeout `requestTimeout + connectTimeout` can be large
  if both are configured aggressively (e.g. 60s + 30s = 90s). Operators that
  need fast shutdown can lower their timeouts; the alternative (a hard cap)
  reintroduces the original bug for legitimate slow polls.
- Two ways to construct a `RemoteProviderConfig` (11-arg overload and 13-arg
  canonical) is a minor API smell. A builder-based replacement is open and out
  of scope for this ADR.

### Migration

Existing users do not need to change anything. The 11-arg constructor keeps
working with sensible defaults for the two new fields. Users that want to opt
into the new behaviour explicitly call the canonical 13-arg constructor or set
the corresponding properties in `application.yaml`.

The internal `OpenFlagsAutoConfiguration` hybrid call site continues to compile
as-is. A follow-up PR may migrate it to read the new properties.

## Alternatives considered

- **Hard-coded body limit**: simpler, no API change, but unable to accommodate
  legitimately large payloads. Rejected: a non-configurable cap is a future
  bug.
- **Streaming truncation instead of `ResponseTooLargeException`**: yields a
  partial body that fails to parse with a confusing JSON error. Rejected: an
  explicit signal is easier to diagnose and route through the existing failure
  path.
- **Drop the 5-second floor in the shutdown await**: minor simplification, but
  configurations with both timeouts under one second would shrink the window
  enough that legitimate polls fail to terminate cleanly. Kept the floor.
- **Migrate off the JDK `HttpClient` entirely (e.g. to OkHttp or a Netty-based
  client)**: would resolve the JDK-specific HTTP/2 fallback quirks but is a
  vastly larger change with new dependencies and a different threading model.
  Out of scope for a hardening PR.
- **Mark the 11-arg overload `@Deprecated(forRemoval = true)` immediately**:
  premature; the canonical constructor is not necessarily the long-term
  preferred entry point either. Deferred until a builder-based API is decided.
- **Builder-based `RemoteProviderConfig`**: cleaner long-term ergonomics and
  removes the record-growth problem entirely. Out of scope for PR 7 because it
  requires a coordinated migration across the starter and the public examples.
  Tracked separately.

## References

- Findings: A-03 (shutdown timeout vs requestTimeout), A-06 (HTTP version
  forced), M-05 (unbounded response body).
- Implementation PR: PR 7 in `data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md`.
- Original draft: `data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md`,
  section "ADR-1".
