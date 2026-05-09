# Upgrade Guide

This document captures the steps required to upgrade between minor and major
versions of openflags. For the full change history see [CHANGELOG.md](../CHANGELOG.md).

## Versioning policy

openflags follows [Semantic Versioning](https://semver.org/):

- **Major** (`x.0.0`): breaking changes to public API, public metric/tag names,
  MDC keys, or default behavior that requires user action.
- **Minor** (`1.x.0`): backwards-compatible additions — new APIs, new providers,
  new metrics, new properties with safe defaults.
- **Patch** (`1.0.x`): bug fixes only. No API or behavior change.

Anything under a package or class annotated `@Internal` is excluded from
this contract.

## Public surface covered by SemVer

- Types in the documented public packages of:
  - `io.github.eliasss3990.openflags.core` (excluding `*.internal`)
  - `io.github.eliasss3990.openflags.provider.file`
  - `io.github.eliasss3990.openflags.provider.remote`
  - `io.github.eliasss3990.openflags.provider.hybrid`
  - `io.github.eliasss3990.openflags.spring`
  - `io.github.eliasss3990.openflags.testing`
- Names and tag keys in `OpenFlagsMetrics`
- MDC keys in `OpenFlagsMdc`
- Spring Boot configuration properties under `openflags.*`
- Defaults documented in the README and the autoconfig metadata

## From `0.x` to `1.0.0`

`0.x` releases were unstable previews and are no longer supported. Direct
upgrades are not supported; treat 1.0.0 as a fresh adoption.

The Maven coordinates changed from `io.github.eliasss3990.openflags` to
`io.github.eliasss3990` at 1.0.0:

```xml
<!-- before, 0.x -->
<dependency>
  <groupId>io.github.eliasss3990.openflags</groupId>
  <artifactId>openflags-core</artifactId>
  <version>0.5.0</version>
</dependency>

<!-- after, 1.0.0 -->
<dependency>
  <groupId>io.github.eliasss3990</groupId>
  <artifactId>openflags-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Java package names (`io.github.eliasss3990.openflags.*`) did **not** change — your imports stay
the same.

## From `1.0.0` to `1.1.0`

### Breaking changes

#### `EvaluationResult` canonical constructor removed

The record gained two new fields (`variant`, `matchedRuleId`), making the
previous 3-arg canonical constructor `new EvaluationResult<>(value, reason, flagKey)`
a binary and source incompatibility.

**Migration**: replace all call sites with the new factory method:

```java
// before
new EvaluationResult<>(value, reason, flagKey)

// after
EvaluationResult.of(value, reason, flagKey)
```

Code using accessor methods (`result.value()`, `result.reason()`, `result.flagKey()`)
is unaffected.

---

### Behavior changes

#### `FlagProvider` lifecycle formalization (ADR-2)

`FlagProvider` now formalizes three lifecycle phases: `created`, `initialized`,
`shutdown`. Implementations must not emit `FlagChangeEvent` during the `created`
phase (before `init()` returns).

If your code registered a change listener on `InMemoryFlagProvider` before
calling `init()` and expected to observe events from pre-init mutations, move the
registration to after `init()` returns. Pre-init mutations are still valid but
are now silent.

#### `OpenFlagsClient.addChangeListener` after shutdown is a no-op

`addChangeListener` called after `shutdown()` is now silently ignored
(previously threw `IllegalStateException`), mirroring the existing behavior
of `removeChangeListener`.

#### `HybridFlagProvider` listener registration and snapshot writes (ADR-3)

- Remote poll listener and remote/file change listeners are now registered
  only after both sub-providers have completed `init()`.
- Snapshot writes are performed on a dedicated daemon thread
  (`openflags-snapshot-writer`) instead of the poller thread. A custom executor
  can be supplied via `HybridFlagProviderBuilder.snapshotExecutor(Executor)`;
  the caller owns its lifecycle in that case.
- Bursty polls coalesce: only the latest snapshot is written per burst;
  intermediate snapshots are intentionally dropped.
- `shutdown()` drains any pending snapshot synchronously before stopping the
  default executor (2-second bounded `awaitTermination`).

---

### Deprecations

#### `ProviderState.STALE`

`ProviderState.STALE` is deprecated (`@Deprecated(forRemoval = true, since = "1.1.0")`)
and scheduled for removal in 2.0. No built-in provider emits this state in 1.x.
Do not add new logic dependent on this value.

Existing passive consumers (health indicator mapping to `OUT_OF_SERVICE`,
Micrometer gauge code `4`) are retained unchanged until 2.0.

#### `OpenFlagsClientBuilder.metricsRegistry(Object)`

`OpenFlagsClientBuilder.metricsRegistry(Object)` is deprecated
(`@Deprecated(forRemoval = true, since = "1.1.0")`) and scheduled for removal
in 2.0. The reflective implementation has been replaced with a direct,
type-checked path.

**Migration**: replace the deprecated call with the new explicit API:

```java
// before
builder.metricsRegistry(meterRegistry)

// after
builder.metricsRecorder(new MicrometerMetricsRecorder(meterRegistry, true))
```

Spring Boot starter users are unaffected — the starter wires the recorder
directly via `MicrometerBindings`.

---

## Future releases

When upgrading between published 1.x versions, follow this order:

1. Read the relevant section in [CHANGELOG.md](../CHANGELOG.md) — every entry
   under `Breaking changes` lists the migration step.
2. Bump the version in your `pom.xml` (use the BOM at `openflags-bom` to keep
   modules aligned).
3. Run `mvn dependency:tree` to confirm transitive versions resolved as expected.
4. Re-run your test suite. The metric and MDC names are stable, so dashboards
   and alerts should not need changes within a major.
5. If you customize providers, check whether new defaults (e.g. timeouts,
   poll intervals) changed in this release — these are documented under
   `Changed` in the CHANGELOG.

## Reporting upgrade issues

If a "non-breaking" upgrade breaks your code, that is a bug — open an issue
with the previous and new version numbers and the failure mode.
