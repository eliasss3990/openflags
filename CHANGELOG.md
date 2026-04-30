# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Breaking Changes (pre-1.0)

- **`EvaluationReason.DEFAULT` renamed to `EvaluationReason.NO_RULE_MATCHED`** — the previous name was ambiguous (it conflated "no rules declared" with "rules declared but none matched"). `RESOLVED` already covers the no-rules case. Callers comparing against `DEFAULT` must update to `NO_RULE_MATCHED`. (C-02, C-12)

### Added

- `openflags-core`: `FlagFileParser` moved from `openflags-provider-file` to `openflags-core` so providers can share file parsing without depending on `provider-file` (B-08, F-14)
- `openflags-provider-remote`: `RemotePollListener.onPollComplete(...)` callback — replaces ad-hoc reflection used by `HybridFlagProvider` to observe polling outcomes (R-08)
- `openflags-spring-boot-starter`: provider lifecycle delegated to Spring container via `@Bean(initMethod, destroyMethod)` (R-09)
- `openflags-testing`: `withObjectFlag(String, Map<String, Object>)` overload in `OpenFlagsTestSupport` (T-12)

### Changed

- `openflags-core`: `FlagValue.asObject()` no longer wraps in `Collections.unmodifiableMap` — `Map.copyOf` already returns an immutable map (C-09)
- `openflags-core`: `FlagFileParser` validates inputs and source labels; rejects malformed YAML/JSON with descriptive errors
- `openflags-provider-remote`: `RemoteProviderConfig` validates auth header name/value (non-blank when configured) and URL scheme (`http`/`https` only) (R-01, R-02, R-18)
- `openflags-provider-remote`: `RemoteHttpClient` validates inputs and propagates configured timeouts consistently
- `openflags-provider-file`: `FileWatcher` validates non-null path and callback; safer error handling around watch loop
- `openflags-provider-file`: `FileFlagProviderBuilder` javadoc warns about symlinks/Docker/K8s ConfigMap behaviour with `WatchService` (F-15)
- `openflags-provider-hybrid`: `SnapshotWriter` uses a single `ObjectMapper` per writer with documented thread-safety reasoning (R-21)
- `openflags-provider-hybrid`: `HybridFlagProvider` switched to standard `java.time` imports rather than fully-qualified references (R-14 readability)
- `openflags-spring-boot-starter`: `OpenFlagsProperties.provider` javadoc clarifies case-sensitivity (`file`/`remote`/`hybrid` lowercase) (R-20)
- `openflags-testing`: `OpenFlagsTestSupport.createTestClient` javadoc expanded with ownership and lifecycle notes (T-13)

### Fixed

- `openflags-testing`: `InMemoryFlagProvider` shutdown atomicity — `initialized`/`watcher` volatile, `shutdown()` sets `SHUTDOWN` state; `remove()` / `clear()` / `putFlag` / `setDisabled` reject calls after shutdown (T-04)
- `openflags-core`: race between reload and shutdown closed; `setDisabled` made atomic
- `openflags-provider-remote`: `init()` raises `ProviderException` with a clear message on 401/403 instead of silently degrading (R-04)
- `openflags-provider-hybrid`: race between snapshot write and `onFileChange` closed by capturing the debounce timestamp before the atomic move
- `openflags-provider-hybrid`: `HybridFlagProvider` thread-safety javadoc expanded to document the volatile-snapshot + synchronized-lifecycle model (R-14)
- core/providers: defensive guards around casts and clearer `Optional` semantics on `FlagChangeEvent`

### Tests

- New: shutdown-vs-evaluation concurrency in `OpenFlagsClient` using `CyclicBarrier` and a shutdown-actually-ran assertion (C-13)
- New: HTTP request timeout in `RemoteHttpClient` via WireMock `withFixedDelay` (R-17)
- New: cache-TTL `DEGRADED → ERROR` transition under sustained backend failure (R-16)
- New: `FlagFileParser` parsing more than `WARN_RULES_PER_FLAG` rules (F-11)
- Statistical 100k-iteration variant-distribution test tagged `@Tag("statistical")` and excluded from the default Surefire run (override with `-DexcludedGroups=`) (C-15)
- Test event accumulators switched from `ArrayList` to `CopyOnWriteArrayList` to remove data races between listener threads and the main test thread (T-14)
- `Optional.get()` on test results replaced with `.orElseThrow()` (T-09); `latch.await(...)` return value asserted (T-10)

### Build

- Activated JaCoCo, Maven Enforcer and Flatten plugins; coverage threshold tuned per module
- Centralised WireMock version in the root pom (`${wiremock.version}=3.10.0`); previously duplicated across hybrid/remote/starter (B-02, B-11)
- Upgraded Spring Boot parent: `3.3.5` → `3.4.7` (B-10). Jackson, Logback, Tomcat and Micrometer remain delegated to the BOM with no overrides.

---

## [0.4.0] - 2026-04-26

### Added

- `openflags-provider-hybrid` (new module): `HybridFlagProvider` — combines `RemoteFlagProvider` (primary) with `FileFlagProvider` (fallback); routes reads to remote when state is READY/DEGRADED, falls back to file when remote is ERROR/NOT_READY
- `openflags-provider-hybrid`: `SnapshotWriter` — serializes `Map<String, Flag>` to JSON or YAML atomically using write-to-temp + `ATOMIC_MOVE`; fallback to `REPLACE_EXISTING` on unsupported filesystems
- `openflags-provider-hybrid`: `HybridProviderConfig` — immutable configuration record with validation (debounce < pollInterval, parent dir must exist, etc.)
- `openflags-provider-hybrid`: `HybridFlagProviderBuilder` — fluent builder mirroring the style of File/Remote builders
- `openflags-provider-hybrid`: `SnapshotFormat` enum — `JSON` or `YAML`
- `openflags-provider-hybrid`: combined state machine — `READY` when remote is READY; `DEGRADED` when remote is degraded or file is serving; `ERROR` when both fail
- `openflags-provider-hybrid`: power-of-two log throttling for consecutive snapshot write failures
- `openflags-provider-hybrid`: debounce filter (default 500ms) to suppress self-induced WatchService events from snapshot writes
- `openflags-spring-boot-starter`: `OpenFlagsProperties.HybridProperties` — Spring Boot properties for `openflags.hybrid.*`
- `openflags-spring-boot-starter`: conditional bean `hybridFlagProvider` activated by `openflags.provider=hybrid`
- `openflags-spring-boot-starter`: `OpenFlagsHealthIndicator` now reports DEGRADED as `outOfService`
- `openflags-bom`: `openflags-provider-hybrid` added to the Bill of Materials

---

## [0.3.0] - 2026-04-25

### Added

- `openflags-core`: `WeightedVariant` record — a single variant with a weight in `[0, 100]`
- `openflags-core`: `MultiVariantRule` record — splits traffic across N weighted variants; weights must sum to 100; max 50 variants; `List.copyOf` enforces immutability
- `openflags-core`: `VariantSelector` — deterministic bucket-to-variant mapping using cumulative weight ranges
- `openflags-core`: `EvaluationReason.VARIANT` — emitted when a `MultiVariantRule` matches
- `openflags-core`: `ProviderState.DEGRADED` — poll failed but cache TTL not yet exceeded
- `openflags-core`: `ProviderState.SHUTDOWN` — provider has been shut down and released all resources
- `openflags-provider-file`: `FlagFileParser.parseFlags(JsonNode, String)` — public API for parsing a pre-deserialized JSON tree with a source label; `parse(Path)` delegates to it
- `openflags-provider-file`: support for `kind: multivariant` rules in YAML and JSON flag files
- `openflags-provider-remote` (new module): `RemoteFlagProvider` — fetches flags from HTTP backend with configurable polling and stale-while-error cache
- `openflags-provider-remote`: `RemoteFlagProviderBuilder` — fluent builder for `RemoteFlagProvider`
- `openflags-provider-remote`: `RemoteProviderConfig` — immutable configuration record with validation
- `openflags-provider-remote`: `RemoteHttpClient` — thin wrapper around `java.net.http.HttpClient` with `Redirect.NEVER` (SSRF mitigation)
- `openflags-spring-boot-starter`: conditional bean `remoteFlagProvider` activated by `openflags.provider=remote`
- `openflags-spring-boot-starter`: `OpenFlagsProperties.RemoteProperties` — Spring Boot configuration properties for the remote provider
- `openflags-bom`: `openflags-provider-remote` added to the Bill of Materials

### Changed

- `openflags-core`: `Rule` sealed interface — `permits` extended to include `MultiVariantRule`
- `openflags-core`: `RuleEngine` — switch extended for `MultiVariantRule`; without `targetingKey` the rule is skipped; with `targetingKey` a variant is selected and `VARIANT` is returned
- `openflags-core`: `Flag` compact constructor — validates that each variant's type matches the flag type
- `openflags-provider-file`: `FlagFileParser` — refactored to extract a public `parseFlags(JsonNode, String)` method; all internal parse methods updated to use `sourceLabel` string instead of `Path`
- Build: `java.version` bumped to 21 to enable pattern matching in switch (sealed interface dispatch)

### Notes

- Minimum `pollInterval` for `RemoteFlagProvider` is 5 seconds to prevent accidental backend overload.
- Reordering variants in a live `MultiVariantRule` shifts cumulative ranges and changes user assignments. Prefer setting weight to 0 to pause a variant without reordering.
- Backend response format is identical to the file provider JSON format (`{ "flags": { ... } }`).

---

## [0.2.0] - 2026-04-25

### Added

- `openflags-core`: `RuleEngine` — evaluates an ordered list of `Rule` instances against an `EvaluationContext` using first-match-wins semantics
- `openflags-core`: `TargetingRule` — attribute-based rule; returns a configured value when all `Condition` entries match the context (AND logic)
- `openflags-core`: `SplitRule` — percentage-rollout rule; assigns users to buckets 0–99 via MurmurHash3 and returns a configured value when the bucket falls within the configured percentage
- `openflags-core`: `Operator` — 12 comparison operators: `EQ`, `NOT_EQ`, `IN`, `NOT_IN`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `NOT_CONTAINS`, `MATCHES`, `NOT_MATCHES`
- `openflags-core`: `BucketAllocator` — consistent hashing allocator backed by `MurmurHash3` for stable percentage rollout
- `openflags-core`: `MurmurHash3` — zero-dependency implementation of the MurmurHash3 x86 32-bit algorithm
- `openflags-core`: `ConditionEvaluator` — applies a single `Condition` to context attributes; handles numeric coercion for `EQ`/`IN`/`NOT_IN`
- `openflags-core`: `Condition` — value object holding `attribute`, `operator`, and `value` fields
- `openflags-core`: new `EvaluationReason` values: `TARGETING_MATCH` (a `TargetingRule` matched), `SPLIT` (a `SplitRule` matched), `DEFAULT` (rules present but none matched)

### Changed

- `openflags-core`: `FlagEvaluator` delegates to `RuleEngine` after the standard enabled/type checks; falls back to the static flag value with reason `DEFAULT` when no rule matches
- `openflags-core`: `Flag` record gains a `rules` field (`List<Rule>`, defaults to empty list) — no breaking change for existing usages
- `openflags-provider-file`: `FlagFileParser` parses the optional `rules:` section from YAML/JSON and hydrates `TargetingRule` and `SplitRule` instances

### Notes

- Full backward compatibility with Phase 1 flags: any flag without a `rules:` section is evaluated as before with reason `RESOLVED`

---

## [Unreleased] — 0.1.0

### Added

- `openflags-core`: `OpenFlagsClient` with typed flag evaluation (`getBooleanValue`, `getStringValue`, `getNumberValue`, `getObjectValue`) and full `EvaluationResult` variants
- `openflags-core`: `FlagProvider` interface — extensible provider contract with lifecycle (`init`, `shutdown`), change listeners, and `ProviderState`
- `openflags-core`: `FlagEvaluator` — stateless evaluation engine with resolution reasons (`RESOLVED`, `FLAG_NOT_FOUND`, `FLAG_DISABLED`, `TYPE_MISMATCH`, `PROVIDER_ERROR`)
- `openflags-core`: `EvaluationContext` — carrier for targeting attributes (used in Phase 2; accepted but not applied in Phase 1)
- `openflags-provider-file`: `FileFlagProvider` — file-based provider with YAML and JSON support, hot reload via `WatchService`, debounce (200 ms), and mid-write retry
- `openflags-provider-file`: `FileWatcher` — daemon-thread file watcher; thread-safe `start()`/`stop()` with idempotent start and rejection of restart after stop
- `openflags-provider-file`: `FlagFileParser` — strict YAML/JSON parser; rejects files without a top-level `flags` key or with empty/null root
- `openflags-spring-boot-starter`: zero-config auto-configuration via `OpenFlagsAutoConfiguration`; resolves classpath and filesystem paths via `ResourceLoader`
- `openflags-spring-boot-starter`: `OpenFlagsHealthIndicator` registered conditionally when Spring Actuator is on the classpath (`@ConditionalOnClass`)
- `openflags-testing`: `InMemoryFlagProvider` — programmatic flag setup for unit and integration tests; supports change listeners and all flag types
- `openflags-bom`: Bill of Materials for consistent dependency management across consumer projects

### Changed

- `FlagFileParser`: empty file and missing `flags` key now throw `ProviderException` instead of silently returning an empty map. `flags: {}` (explicit empty object) remains valid and returns an empty map.
- `FlagEvaluator.toFlagType()`: accepts `HashMap`, `LinkedHashMap`, and any `Map` subclass for `OBJECT` flags (previously required exact `Map.class`).
- `OpenFlagsClient.removeChangeListener()`: safe to call after `shutdown()` — behaves as a no-op to allow unconditional cleanup paths.
- `FlagValue.of()` with `OBJECT` type: stores an immutable copy of the input map (`Map.copyOf`). Mutations to the original map after construction are not reflected.

### Deprecated

- `FlagValue.getRawValue()`: deprecated since 0.1.0, will be removed in a future release. Use the typed accessors (`asBoolean()`, `asString()`, `asNumber()`, `asObject()`) instead.

### Fixed

- `FileFlagProvider.reload()`: concurrent `shutdown()` during an in-progress reload no longer leaves the provider in `ERROR` state; the state correctly remains `STALE` after shutdown.
- `FileFlagProvider.init()`: `FileWatcher.start()` now rejects a restart after `stop()`, preventing leaked threads if `init()` is called after `shutdown()`.
- `OpenFlagsAutoConfiguration`: `OpenFlagsHealthIndicator` is now registered via a conditional inner `@Configuration` class, fixing a bug where the bean was never created when using component scan from an external application.
- `InMemoryFlagProvider.init()`: calling `init()` after `shutdown()` now throws `IllegalStateException` instead of silently resetting the provider to `READY`.
- `FlagValue.validate()`: type check for `OBJECT` flags now uses `instanceof Map<?,?>` instead of raw `instanceof Map`, eliminating an erroneous `@SuppressWarnings` and reducing cognitive complexity (extracted `isCompatible()` helper).
- `OpenFlagsAutoConfiguration`: removed unused `Logger` field that was left after the classpath-inside-JAR handling was changed to throw `IOException` instead of logging.

---

[Unreleased]: https://github.com/eliasss3990/openflags/commits/main
