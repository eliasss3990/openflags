# 2. FlagProvider lifecycle: formal phases and listener registration order

Date: 2026-05-03

## Status

Accepted

## Context

The `FlagProvider` SPI implicitly assumes a lifecycle of three phases —
`created`, `initialized` (post-`init()`), and `shutdown` — but the contract is
neither documented in Javadoc nor enforced by any state machine. Several bugs
in the 1.0 line trace back to this ambiguity:

- **T-01** — `InMemoryFlagProvider.setBoolean` (and analogous setters) emit
  `FlagChangeEvent` instances even when the provider has been created but
  `init()` has not yet been invoked. Listeners registered during construction
  observe events that, by contract, should only be visible to an initialized
  provider.
- **T-03** — `HybridFlagProvider` registers its internal poll listener
  (`this::onPollComplete`) inside the constructor (current lines 171 and 188),
  *before* `remote.init()` runs. Because `RemoteFlagProvider.init()` performs a
  synchronous `pollOnce()` as part of its initialization sequence, that first
  poll fires the freshly-registered listener, which in turn calls `writeSafe()`
  and writes the snapshot file. The internal `FileWatcher` then captures that
  write as if it were an external change. The current code papers over this
  with `expectingSelfWrite.set(false)` at line 257.
- **B-01** — `OpenFlagsClient.addChangeListener` throws after `shutdown()`
  while `removeChangeListener` is silently a no-op. The asymmetry is
  undocumented and surprises consumers writing teardown logic.
- **N-01** — There is no language-level distinction between *lifecycle state*
  (created/initialized/shutdown) and *health state* (`HEALTHY`, `DEGRADED`,
  `ERROR`). Both are conflated in `ProviderState`.

### The current `HybridFlagProvider.init()` shape (conceptual)

```java
public HybridFlagProvider(...) {
    // ...wiring...
    this.remote.setPollListener(this::onPollComplete);   // (a) too early
    this.file.addChangeListener(this::onFileChange);
}

public void init() {
    remote.init();                 // executes a synchronous pollOnce()
                                   // -> fires listener registered at (a)
                                   // -> writeSafe() writes snapshot
                                   // -> FileWatcher sees a "change"
    file.init();
    expectingSelfWrite.set(false); // line 257: defensive reset to swallow
                                   // the spurious self-write event
}
```

The `expectingSelfWrite` flag is therefore not a feature; it is a workaround
for the listener-registered-too-early ordering bug. A previous iteration of
the plan proposed eliminating the flag while keeping the early registration —
that combination would reintroduce the bug.

### The proposed shape

```java
public HybridFlagProvider(...) {
    // ...wiring only; no listener registration on remote/file...
}

public void init() {
    remote.init();                                    // synchronous pollOnce()
                                                      // fires no listener: none registered yet
    file.init();

    remote.addChangeListener(this::onRemoteChange);
    file.addChangeListener(this::onFileChange);
    remote.setPollListener(this::onPollComplete);     // late registration
    // expectingSelfWrite removed entirely
}
```

By construction, the first synchronous `pollOnce()` triggered by
`remote.init()` cannot fire `onPollComplete`, because the listener does not
exist yet. The snapshot is written for the first time only after the next
poll cycle, by which point the `FileWatcher` is fully initialized and the
write goes through the normal `expectingSelfWrite` discipline — except that
discipline is no longer needed for the init path and the flag itself can be
deleted.

## Decision

We formalize the `FlagProvider` lifecycle for the 1.x line through three
coordinated changes. A structural redesign — separate `LifecycleState` and
`HealthState` enums — is deferred to 2.0 (see ADR-7).

### 1. Document three lifecycle phases in Javadoc

The Javadoc of `FlagProvider` is updated to describe explicitly:

- **`created`** — the provider has been constructed but `init()` has not yet
  been invoked. The provider must not emit `FlagChangeEvent` instances during
  this phase. Setters that mutate flag state are permitted, but their effects
  must be visible only after `init()` returns. Implementations are encouraged
  to either reject mutating calls or buffer them until initialization.
- **`initialized`** — `init()` has completed successfully. The provider is
  expected to honor evaluation calls, emit change events, and report a
  meaningful `ProviderState`.
- **`shutdown`** — `shutdown()` has been invoked. `addChangeListener` and
  `removeChangeListener` are both no-ops; evaluation calls behave as
  documented per provider but must not throw `NullPointerException` on
  internal state.

### 2. Late listener registration in `HybridFlagProvider`

`HybridFlagProvider` registers `pollListener`, `remoteChangeListener` and
`fileChangeListener` inside `init()`, *after* both `remote.init()` and
`file.init()` have returned. The constructor performs wiring only (capturing
references) but does not subscribe to any upstream component.

### 3. Remove `expectingSelfWrite`

With late registration in place, the first synchronous `pollOnce()` performed
by `remote.init()` cannot reach `onPollComplete`. The defensive
`expectingSelfWrite.set(false)` at line 257 becomes structurally unreachable
and is removed together with the `AtomicBoolean` field. Subsequent self-write
suppression (the case where Hybrid writes a snapshot and `FileWatcher` later
notices it) continues to be handled by the existing `expectingSelfWrite`
discipline around `writeSafe()` — that discipline is preserved; only the
init-time reset is removed.

### 4. Symmetric listener API around shutdown

`OpenFlagsClient.addChangeListener` is changed to a no-op when invoked after
`shutdown()`, matching the pre-existing behavior of `removeChangeListener`.
The Javadoc of both methods documents the post-shutdown contract explicitly.

## Consequences

### Positive

- The first poll during initialization no longer produces a spurious snapshot
  write, eliminating an entire class of `FileWatcher` race conditions.
- One mutable boolean field disappears from `HybridFlagProvider`, reducing
  the state surface that future maintainers must reason about.
- `addChangeListener` and `removeChangeListener` become symmetric, removing a
  papercut for shutdown code paths.
- The lifecycle contract is explicit, paving the way for the structural
  separation of lifecycle and health states in 2.0 (ADR-7).

### Negative / Migration

This is an **observable behavior change** for consumers that registered
listeners before invoking `init()` on `InMemoryFlagProvider` and relied on
seeing pre-init events. After this change, listeners registered before
`init()` will receive no events until initialization completes. The CHANGELOG
for the release that ships this ADR must document this explicitly:

> **Behavior change** — `FlagProvider` implementations now formalize three
> lifecycle phases (`created`, `initialized`, `shutdown`). Change events are
> only emitted during the `initialized` phase. Code that relied on observing
> events from listeners registered before `init()` must move the registration
> to after `init()` returns, or accept that pre-init mutations are silent.

### Risk

The removal of `expectingSelfWrite.set(false)` in `init()` is safe only as
long as listener registration is moved *after* `remote.init()`. A regression
test (`HybridFlagProviderTest#firstPollDuringInitDoesNotWriteSnapshot`)
locks this invariant: a fake `RemoteFlagProvider` whose `init()` invokes the
poll listener if registered must not cause a snapshot write during Hybrid's
own `init()`.

## Alternatives considered

### A. Register listener early, keep `expectingSelfWrite` as explicit guard

Register `pollListener` in the constructor (status quo) and document
`expectingSelfWrite.set(false)` as the intentional guard against the
init-time self-write. Rejected because it preserves a workaround as the
contract: the flag exists solely to mask an ordering issue that can be
removed structurally. Future maintainers reading the field would have to
reconstruct the rationale from history.

### B. Buffer events emitted before `init()` and replay them post-init

Have `InMemoryFlagProvider` queue events emitted in the `created` phase and
replay them once `init()` returns. Rejected because event ordering across the
init boundary is not a contract anyone has asked for, and the buffer is a new
failure mode (memory growth if `init()` is never called).

### C. Introduce `LifecycleState` enum in 1.x

Split the existing `ProviderState` into `LifecycleState` and `HealthState`
immediately. Rejected for 1.x because it is a breaking change to the SPI;
deferred to 2.0 (ADR-7).

## References

- Findings: T-01, T-03, B-01, N-01 (see
  `data/proyectos/openflags/main/plan/code-review-arquitectura/04-hallazgos.md`).
- Implementation PR: PR 8 (see
  `data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md`).
- Related ADRs: ADR-3 (threading policy — listener execution thread),
  ADR-7 (2.0 structural lifecycle / health split).
