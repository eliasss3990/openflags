# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
