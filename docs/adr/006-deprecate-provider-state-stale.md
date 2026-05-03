# 6. Deprecate ProviderState.STALE

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

`com.openflags.core.provider.ProviderState` is the public enum that every
`FlagProvider` exposes through `getState()`. Today it declares five values:
`NOT_READY`, `READY`, `DEGRADED`, `STALE`, and `ERROR`. Of those, `STALE` is
the only one that no provider actively transitions into in the current
codebase. A repository-wide search (PR 12a hallazgos M-01 and N-06) confirms
the asymmetry:

- **Producers (none).** Neither `InMemoryFlagProvider`,
  `FileFlagProvider`, `RemoteFlagProvider`, nor `HybridFlagProvider`
  contains a `transitionTo(STALE)` (or equivalent) call. The only places
  the literal `STALE` appears in production code outside the enum
  declaration itself are passive consumers, not emitters.
- **Consumers.** `OpenFlagsHealthIndicator` maps `DEGRADED, STALE` to
  `Health.outOfService()` in a single switch arm.
  `MicrometerMetricsRecorder` assigns the gauge value `4` to `STALE` so
  that the metric encoding does not have a hole in its ordinal mapping.
  The README and `docs/configuration.md` mention `STALE` only to describe
  the health-indicator mapping. `docs/providers.md` lists `STALE` as one
  of the values a provider "may" return, but no provider in this repo
  ever returns it. Tests reference the value to exercise the metrics
  recorder and the `ProviderEvent` toString — both unit-level concerns,
  not behavioral coverage of a real provider transition.
- **Historical traces.** The CHANGELOG shows two old entries (lines 50
  and 267) about `FileFlagProvider` race conditions where `STALE` was
  produced or expected during reload/shutdown. Those code paths were
  reworked and no longer leave the provider in `STALE`; the CHANGELOG
  entries describe behavior that is no longer present.

The net effect is that `STALE` is dead weight in the public SPI:

1. It signals to integrators that providers may return it, but in
   practice they never will. Switch statements that handle it are
   defensive code for an event that does not occur.
2. The `HealthIndicator` mapping bundles `STALE` with `DEGRADED`, which
   blurs the contract that `ProviderState` is supposed to express.
3. Removing it outright in 1.x would be a binary-incompatible change
   for any downstream code that references `ProviderState.STALE` in a
   `switch` or in reflection-based code, even though no provider ever
   emits it. A 1.x release that deletes a public enum constant would
   break source compatibility for every consumer doing exhaustive
   matching.

The plan documents (`05-plan-ejecucion.md`, section "PR 12a", and
`06-adrs-pendientes.md`, section "ADR-6") prescribe a two-step path:
deprecate now in 1.x, remove in 2.0. This ADR formalizes that decision.

## Decision

`ProviderState.STALE` is deprecated in 1.x and scheduled for removal in
2.0. Concretely:

1. The enum constant is annotated with
   `@Deprecated(forRemoval = true, since = "1.x")` and its Javadoc is
   expanded to state that no provider in this codebase emits the value
   today, that consumers should not rely on receiving it, and that the
   value will be removed in 2.0.
2. `OpenFlagsHealthIndicator.health()` keeps the existing
   `case DEGRADED, STALE -> Health.outOfService()` arm. The method (or
   the file) carries a `@SuppressWarnings("deprecation")` and an inline
   comment pointing to the future PR 12b that will drop the arm.
3. `MicrometerMetricsRecorder` keeps the `case STALE -> 4` arm under
   the same `@SuppressWarnings("deprecation")` rationale, so that the
   gauge encoding stays stable for the lifetime of 1.x. The numeric
   slot is reserved; no other state will be remapped to `4` in 1.x.
4. Documentation (`README.md`, `docs/configuration.md`,
   `docs/providers.md`) is updated to describe `STALE` as deprecated
   and to instruct integrators not to rely on it. The mapping table in
   `docs/configuration.md` retains the `STALE -> OUT_OF_SERVICE` row
   with a footnote noting the deprecation.
5. The CHANGELOG for the 1.x release carrying this change announces
   the deprecation under "Deprecated"; the CHANGELOG for 2.0 will
   announce the removal under "Removed" (PR 12b).
6. No new code path emits `STALE`. The decision is explicitly to remove
   it, not to retroactively wire up a provider to produce it.

This ADR is scoped to 1.x. The companion 2.0 work (PR 12b) is out of
scope here and will be tracked under ADR-7 alongside other 2.0
structural changes.

## Consequences

### Positive

- **Smaller, more honest SPI surface.** The advertised states reduce
  to those that providers actually emit. New integrators reading the
  enum and the providers' Javadoc will see a coherent contract.
- **Single source of truth for "something is wrong but the provider is
  still serving values".** That role belongs to `DEGRADED`. Folding
  `STALE` into `DEGRADED` (or into `READY` plus an `EvaluationContext`
  signal, as 2.0 may explore) removes a duplicated semantic.
- **Cleanup signal for downstream code.** Consumers that do
  `switch (state)` get a deprecation warning at compile time and can
  decide how to handle the constant before 2.0 lands.
- **No behavioral change in 1.x.** Health indicator output, metric
  values, and SPI semantics are byte-for-byte identical to the prior
  1.x release. The change is purely declarative.

### Negative / Risks

- **Compile-time warnings in consumer code.** Any downstream `switch`
  on `ProviderState` that names `STALE` will emit a deprecation
  warning. Consumers who treat warnings as errors (`-Werror`) need to
  add `@SuppressWarnings("deprecation")` or update their code. This is
  the intended signal, but it is still friction. The CHANGELOG entry
  must call this out so it is not a surprise.
- **Reflective lookups continue to succeed.** `ProviderState.valueOf("STALE")`
  keeps working in 1.x. Code that depends on this contract will only
  break in 2.0 (PR 12b). That is acceptable but worth flagging in the
  release notes.
- **Documentation drift risk.** The README, providers and configuration
  docs all reference `STALE`. If the deprecation note is added in only
  some of them, integrators may read the un-updated copy and assume
  the value is fully supported. Mitigation: update all three in the
  same PR (PR 12a) and add a checklist item to the PR description.
- **Test maintenance.** `MicrometerMetricsRecorderTest` and
  `ProviderEventTest` continue to reference `STALE` and will emit
  deprecation warnings under `-Werror`. They get
  `@SuppressWarnings("deprecation")` rather than removal, since they
  guard the encoding contract that 1.x must preserve.
- **Two-step removal is more work than a single-step removal.** The
  project commits to a follow-up PR (12b) and a CHANGELOG entry in the
  2.0 line. The benefit of source compatibility in 1.x is judged worth
  the extra coordination.

### Migration

Consumers fall into two groups:

1. **Switch over `ProviderState` without a `default` arm.** They will
   see a deprecation warning on the `STALE` arm in 1.x and a compile
   error in 2.0. Recommended action in 1.x: keep the arm and add a
   `@SuppressWarnings("deprecation")` if they treat warnings as
   errors. In 2.0 they must delete the arm.
2. **Switch over `ProviderState` with a `default` arm, or no switch at
   all.** Most integrators fall here; they receive `STALE` from no
   provider and never observe it. No source change is required in
   1.x. In 2.0 their code keeps compiling because the `default` arm
   absorbs the structural change.

Code that uses `ProviderState.valueOf("STALE")` (rare, typically only
in tests or admin tooling) needs to switch to a different state name
before upgrading to 2.0; this ADR does not require any change in 1.x
for that pattern.

`OpenFlagsHealthIndicator` consumers do not need to do anything: the
indicator continues to map `DEGRADED` and `STALE` to
`OUT_OF_SERVICE` for the entire 1.x line, and in 2.0 only `DEGRADED`
will remain (the observable mapping for any state a provider actually
emits stays the same).

## Alternatives considered

- **Keep `STALE` and document the criterion under which a provider
  should emit it.** This was the original 0.x intent: `STALE` would
  be set by a cache-backed provider when the cache is older than a
  configurable threshold but still served. Rejected because (a) no
  provider in this repo implements such a cache today; (b) the 2.0
  direction (see ADR-7) prefers decoupling lifecycle from health
  rather than adding more health values; (c) introducing a real
  emitter for `STALE` in 1.x would itself be a behavioral change for
  integrators relying on the current "providers never go STALE"
  assumption.
- **Remove `STALE` immediately in 1.x without deprecation.** Rejected
  because it breaks source compatibility for any consumer doing
  exhaustive switch matching, even though no provider ever emits the
  value. The library's 1.x compatibility promise rules this out.
- **Map `STALE` to `DEGRADED` at runtime via a translation layer.**
  Rejected: it leaves the dead constant in the SPI and adds a
  one-way mapping that 2.0 would still have to undo. Net negative.
- **Rename `STALE` to a different semantic (for example `INITIALIZING`).**
  Rejected: renaming an enum constant is also source-breaking, and
  there is no current need for a state with that name. The 2.0 lifecycle
  rework (`LifecycleState { CREATED, INITIALIZED, SHUTDOWN }`, see
  ADR-7) is the right place to address initialization explicitly.

## References

- PR 12a — Deprecate `ProviderState.STALE` in 1.x (this ADR).
- PR 12b — Remove `ProviderState.STALE` in 2.0 (out of scope here;
  tracked under ADR-7).
- `data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md`,
  section "ADR-6 — `ProviderState` slim down: drop unused `STALE`".
- `data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md`,
  section "PR 12a — Deprecar `ProviderState.STALE` en 1.x (ADR-6)".
- Hallazgos M-01 (no provider emits `STALE`) and N-06
  (HealthIndicator/Metrics consumers) from the architecture review.
- ADR-7 — (2.0) Lifecycle structural y `FlagChangeEventV2`, for the
  follow-up removal and the broader lifecycle rework.
