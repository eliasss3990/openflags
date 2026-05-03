# 5. Add ENABLED/DISABLED change types for boolean flag transitions

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

`FlagChangeEvent` is the public record emitted by every `FlagProvider`
implementation when it observes a difference between two consecutive flag
states. Today the record exposes a `ChangeType` enum with three values:

```java
public enum ChangeType {
    CREATED,
    UPDATED,
    DELETED
}
```

The event also carries `Optional<FlagValue> oldValue` and
`Optional<FlagValue> newValue`. Both `FlagValue` slots reflect the current
typed payload of the flag (boolean, string, integer, double, json), and
nothing else. In particular, **the `enabled` boolean of the underlying
`Flag` model is not part of `FlagValue`** — it lives one level up, on the
flag itself.

This shape has two concrete problems, both surfaced during the architecture
review (findings T-02 and N-02 in
`/data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md`,
ADR-5 source):

1. **T-02 — boolean value transitions are not classified.** A
   boolean-typed flag whose `value` flips between `true` and `false` is
   the most operationally important transition in a feature-flag system
   (turning a feature on or off in production), yet today it surfaces
   indistinguishably from any other change as `ChangeType.UPDATED`.
   Consumers that want to alert specifically on "feature turned off"
   must inspect both `oldValue` and `newValue` and re-derive the
   classification themselves on every event.
2. **N-02 — UPDATED is overloaded.** Today `UPDATED` covers every
   non-create / non-delete transition: value changed (boolean or other),
   default changed, metadata changed, even toggles of the `enabled`
   field. The boolean-value flip is the case worth promoting; the rest
   stay grouped under `UPDATED` for 1.x and get finer classification in
   2.0 via `FlagChangeEventV2` (ADR-7).

> **Scope note.** The `enabled` field of the `Flag` model is *not* part
> of `FlagValue` and is not visible to `resolveUpdate(FlagType, FlagValue,
> FlagValue)`. Detecting flips of the `enabled` field as a distinct
> change type is therefore out of scope for 1.x — it requires a
> structural change to `FlagChangeEvent` and is deferred to ADR-7.
> ENABLED/DISABLED here mean **boolean value transitions**, not
> `enabled`-field toggles.

The 1.x line of openflags is committed to source-compatible evolution. We
cannot:

- add components to the `FlagChangeEvent` record (records are nominally
  typed; adding components changes the canonical constructor and breaks
  every call site that builds the event);
- replace the event with a `FlagChangeEventV2` (that path is reserved for
  2.0, see ADR-7 in the planning doc).

What we *can* do is widen the existing `ChangeType` enum. Adding values to
a public enum is binary-compatible and source-compatible for every
consumer except those that write `switch` statements without a `default`
clause and compile with `-Werror` (see Migration below). That cost is
acceptable for the value it unlocks.

## Decision

We add two new values to `com.openflags.core.event.ChangeType`:

```java
public enum ChangeType {
    /** The flag did not previously exist and was added. */
    CREATED,
    /** The flag existed and its typed value was modified. */
    UPDATED,
    /** The flag was removed from the provider. */
    DELETED,
    /** A boolean flag's value transitioned from {@code false} to {@code true}. */
    ENABLED,
    /** A boolean flag's value transitioned from {@code true} to {@code false}. */
    DISABLED
}
```

Semantics:

- `ENABLED` is emitted when, between two consecutive snapshots of the
  same flag key, the flag is `BOOLEAN`-typed and its `value` transitions
  from `false` to `true`.
- `DISABLED` is the symmetric case: a `BOOLEAN`-typed flag whose `value`
  transitions from `true` to `false`.
- `UPDATED` keeps its current meaning *and* remains the catch-all for
  any other transition (non-boolean value change, equal boolean values,
  changes to the `enabled` field while `value` stays put, default value
  changed, metadata-only changes, etc.). It is intentionally a generic
  bucket; consumers that need finer-grained classification — including
  flips of the `enabled` field — will get more change-type signal in
  2.0 via `FlagChangeEventV2` (ADR-7).
- `CREATED` and `DELETED` win over `ENABLED`/`DISABLED` when they apply.
  A newly-created boolean flag with `value=true` emits `CREATED`, not
  `ENABLED`. Likewise for deletion. This preserves the invariant that
  `oldValue` is empty iff `changeType == CREATED` and `newValue` is
  empty iff `changeType == DELETED`.

Provider responsibilities:

- `InMemoryFlagProvider` and `RemoteFlagProvider` (and, by composition,
  `HybridFlagProvider`) classify each diff into exactly one `ChangeType`
  using the precedence above: `CREATED` > `DELETED` > `ENABLED`/`DISABLED`
  > `UPDATED`.
- The `oldValue` / `newValue` slots are populated as today: the typed
  values of the flag before and after the transition. For an `ENABLED`
  event `oldValue` carries `FlagValue(false)` and `newValue` carries
  `FlagValue(true)`; for `DISABLED` the inverse. Consumers can rely on
  `oldValue` and `newValue` being non-equal whenever
  `changeType == ENABLED || changeType == DISABLED`.

This decision applies to the 1.x line. ADR-7 covers a structural redesign
in 2.0 (`FlagChangeEventV2` exposing the full `Flag`, with `enabled` as a
first-class field).

## Consequences

### Positive

- Listeners can react specifically to enable/disable transitions without
  inspecting provider-internal state. The most operationally important
  transition in a feature-flag system is now first-class.
- `UPDATED` becomes meaningful again: it covers genuine value/metadata
  changes, not the union of "anything that is not create or delete".
- Source-compatible evolution: no record component added, no signature
  changed, no class moved. Existing producers and consumers keep
  compiling and linking against the new artifact without modification.
- Aligns the SPI with what observability tooling already wants to do —
  emit a different counter / log line / alert for "flag turned off in
  production" vs "flag value tweaked".

### Negative / Risks

- `switch` statements over `ChangeType` that *do not* declare a `default`
  branch will produce `MissingCasesInEnumSwitch` warnings under `javac`
  and equivalent diagnostics under most IDEs and linters. Consumers
  compiling with `-Werror` will see a hard build failure until they add
  the missing branches or a `default`. This is the unavoidable cost of
  widening an enum.
- ENABLED/DISABLED do **not** capture flips of the `Flag.enabled` field
  (the toggle that disables flag evaluation regardless of `value`).
  Those transitions still emit `UPDATED` in 1.x and require inspecting
  the provider's current snapshot to detect. Promoting them to a
  first-class change type requires structural changes to
  `FlagChangeEvent` and is deferred to ADR-7 (2.0).
- Provider implementations that compute diffs in batch must adopt the
  precedence rule consistently. Divergent classification across
  providers (e.g. `RemoteFlagProvider` emitting `UPDATED` while
  `InMemoryFlagProvider` emits `DISABLED` for the same transition) would
  be worse than the current state. This is enforced by tests rather than
  by the type system.
- The `oldValue` / `newValue` carry typed payloads only. A consumer that
  needs the *previous* `enabled` field cannot derive it from the event;
  it can only infer it from the `ChangeType`. For 1.x this is acceptable
  (the `ChangeType` carries the bit); 2.0 fixes it structurally via
  `FlagChangeEventV2`.

### Migration

Listeners that use exhaustive `switch` over `ChangeType` must add cases
for `ENABLED` and `DISABLED`, or add a `default` branch. The compile-time
warning makes the missing cases trivially discoverable.

Before (1.x pre-PR-11 — exhaustive without default, compiles clean):

```java
flagProvider.addChangeListener(event -> {
    switch (event.changeType()) {
        case CREATED -> onCreate(event);
        case UPDATED -> onUpdate(event);
        case DELETED -> onDelete(event);
    }
});
```

After (1.x post-PR-11 — explicit handling for the new values):

```java
flagProvider.addChangeListener(event -> {
    switch (event.changeType()) {
        case CREATED -> onCreate(event);
        case UPDATED -> onUpdate(event);
        case DELETED -> onDelete(event);
        case ENABLED -> onEnabled(event);   // new
        case DISABLED -> onDisabled(event); // new
    }
});
```

Or, for consumers that are happy to keep the legacy bucket:

```java
flagProvider.addChangeListener(event -> {
    switch (event.changeType()) {
        case CREATED -> onCreate(event);
        case DELETED -> onDelete(event);
        case UPDATED, ENABLED, DISABLED -> onUpdate(event);
    }
});
```

Or, with a `default` branch (idiomatic for forward-compatible code):

```java
flagProvider.addChangeListener(event -> {
    switch (event.changeType()) {
        case CREATED -> onCreate(event);
        case DELETED -> onDelete(event);
        default      -> onUpdate(event);
    }
});
```

No runtime migration is required. Existing serialized events continue to
deserialize because the wire format of the enum is unchanged for the
existing values, and no new values are produced by older providers.
Consumers receiving `ENABLED` / `DISABLED` from a newer provider while
still running an older `ChangeType` definition is the only failure mode,
and it is the standard "old client, new server" enum problem; the project
already requires version alignment between core and providers within the
same minor line.

## Alternatives considered

### A. Keep only UPDATED and let listeners diff the flag themselves

Rejected. Listeners do not have access to the underlying `Flag` model
through the event — they only see typed `FlagValue` slots. To diff the
`enabled` field they would need to query the provider on every UPDATED
event, which is racy (the provider's state may have advanced again),
expensive (N round-trips per change), and only works at all when the
provider exposes a synchronous getter. This pushes complexity onto every
single listener and still leaves silent enable/disable transitions
undetected for consumers that compare `oldValue` / `newValue`.

### B. Add `oldEnabled` / `newEnabled` components to the record

Rejected. Java records are nominally typed; adding components changes the
canonical constructor signature and breaks every producer that builds
`FlagChangeEvent` via the canonical constructor. Within openflags this
includes `InMemoryFlagProvider`, `RemoteFlagProvider`, the hybrid
composition layer, and a non-trivial set of tests. Outside openflags it
breaks every downstream test fixture or fake that builds events.
Source-incompatible by construction; reserved for 2.0 (`FlagChangeEventV2`,
ADR-7).

### C. Introduce a parallel `FlagStateChangeEvent`

Rejected. Two parallel event hierarchies double the number of listener
APIs (`addChangeListener` vs `addStateChangeListener`), force consumers
to subscribe to both to get a complete picture, and create ordering
questions across the two streams (what if the same poll observes a
value change *and* an enable flip — is the order of the two events
deterministic across providers?). Strictly worse than widening the
existing enum.

### D. Reuse `UPDATED` and add a sub-classification field

E.g. `ChangeType.UPDATED` plus a new `Optional<UpdateKind>` component on
the record. Rejected for the same record-shape reason as alternative B,
and because it pushes consumers through an extra optional unwrap for the
common case.

## References

- `/data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md` — ADR-5 source (section "ADR-5 — `FlagChangeEvent` semantics for non-value changes")
- `/data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md` — PR 11 ("Parche `ChangeType` para `enabled` y oldValue correcto")
- Findings T-02 (silent enable/disable transitions) and N-02 (UPDATED overloaded) in the same architecture review
- ADR-7 (planned, 2.0) — `FlagChangeEventV2` with full `Flag` payload, structural fix
- `com.openflags.core.event.ChangeType` — enum widened by this ADR
- `com.openflags.core.event.FlagChangeEvent` — record whose semantics this ADR refines
