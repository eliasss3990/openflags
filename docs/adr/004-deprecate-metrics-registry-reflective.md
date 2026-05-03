# 4. Deprecate reflective metricsRegistry(Object) builder API

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

`OpenFlagsClientBuilder` exposes a convenience entry point that accepts a
Micrometer `MeterRegistry` typed as `java.lang.Object` and wires it into the
client through reflection:

```java
public OpenFlagsClientBuilder metricsRegistry(Object meterRegistry) { ... }
```

Internally the builder loads `com.openflags.metrics.micrometer.MicrometerMetricsRecorder`
reflectively, walks two different classloaders (the caller's and the library's),
invokes `setAccessible(true)` on the constructor, and instantiates the recorder
via `Constructor.newInstance(...)`. The result is then assigned to the regular
`MetricsRecorder` slot.

This shape was originally introduced so that consumers who did not have
Micrometer on their classpath at compile time could still hand a registry to
the builder without a hard dependency. In practice it has produced more friction
than value:

- **Hallazgo A-04 (type-safety / fragility)**: the `Object` parameter accepts
  anything at compile time. Wrong types fail at runtime with a reflective
  `IllegalArgumentException` whose message is several frames removed from the
  user's call site. There is no IDE assistance, no autocomplete on the registry
  type, and no static guarantee that the symbol resolved by reflection matches
  the symbol the user thinks they are passing.
- **Classloader coupling**: the reflective lookup uses two classloaders (the
  thread context classloader and the builder's own classloader). In containers
  with non-trivial classloader hierarchies (Spring Boot fat-jars, OSGi,
  application servers), the lookup can resolve a different `MeterRegistry`
  class than the one the caller imported, producing silent metric loss.
- **`setAccessible(true)` on a public constructor** is a code smell and trips
  static analysis (SpotBugs, security scanners) because it broadens the surface
  the JVM allows the library to touch under a `SecurityManager` or under the
  newer module access checks.
- **A direct path already exists**. For Spring users, `MicrometerBindings` in
  the starter wires the recorder without reflection. For non-Spring users, the
  recorder is a public class with a public constructor: `new
  MicrometerMetricsRecorder(meterRegistry)` is one line and fully type-safe.

The reflective bridge no longer earns its complexity. We want to retire it
without breaking anyone who is currently relying on it.

## Decision

We deprecate the reflective overload in 1.x and remove it in 2.0. Concretely:

### 1. Add a typed overload

Introduce a new builder method that takes the concrete Micrometer type:

```java
public OpenFlagsClientBuilder metricsRecorder(MetricsRecorder recorder) { ... }
```

The typed entry point already exists conceptually (`metricsRecorder` is the
canonical setter). The reflective `metricsRegistry(Object)` becomes a thin
deprecated wrapper that constructs `new MicrometerMetricsRecorder(meterRegistry)`
and delegates to `metricsRecorder(...)`.

When `MicrometerMetricsRecorder` is not on the classpath, the deprecated method
fails fast with a clear, non-reflective error pointing at the new API. The
reflective classloader walk is removed.

### 2. Mark the reflective overload `@Deprecated(forRemoval = true)`

```java
/**
 * @deprecated Use {@link #metricsRecorder(MetricsRecorder)} with a
 *             {@link com.openflags.metrics.micrometer.MicrometerMetricsRecorder}
 *             instance. The reflective bridge will be removed in 2.0.
 *             <p>Migration example:
 *             <pre>{@code
 * // Before
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *         .provider(provider)
 *         .metricsRegistry(meterRegistry)  // reflective bridge
 *         .build();
 *
 * // After
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *         .provider(provider)
 *         .metricsRecorder(new MicrometerMetricsRecorder(meterRegistry))
 *         .build();
 *             }</pre>
 */
@Deprecated(forRemoval = true, since = "1.x")
public OpenFlagsClientBuilder metricsRegistry(Object meterRegistry) { ... }
```

The same treatment applies to any equivalent reflective entry point on
`RemoteFlagProviderBuilder` if one exists in the same shape (open: confirmed
during PR 10 implementation).

### 3. Document the migration path

`docs/integrations.md` gains a short "Metrics" section showing both the Spring
Boot starter route (`MicrometerBindings` registers the recorder automatically)
and the manual route (`new MicrometerMetricsRecorder(registry)`).

### 4. Removal in 2.0

The 2.0 branch deletes the reflective overload and its supporting
private helpers (classloader walk, `setAccessible` calls, reflective
constructor lookup). The CHANGELOG for 2.0 calls this out explicitly under
"Removed".

## Consequences

### Positive

- Compile-time type safety: `MeterRegistry` is the parameter type on the
  recommended path; wrong types fail at compile time.
- Fewer classloader surprises: the recorder is constructed in the caller's
  classloader, with the caller's `MeterRegistry`. There is no two-classloader
  reconciliation step.
- `setAccessible(true)` and the reflective constructor lookup disappear from
  the library, simplifying static analysis output and reducing the surface
  exposed under the module system.
- IDE autocomplete and Javadoc tooltips work as expected on the recommended
  API.

### Negative / Risks

- Consumers who deliberately wanted to avoid a compile-time dependency on
  Micrometer must now either (a) keep using the deprecated method during 1.x
  and migrate before 2.0, or (b) introduce the dependency. For most users the
  dependency is already transitive via the starter, so this is mostly a
  documentation problem.
- Deprecation warnings will surface in user builds that have `-Werror`
  enabled. The Javadoc migration example is the mitigation: the fix is
  mechanical and visible at the warning site.
- 2.0 removal is a hard breaking change for any caller that did not migrate.
  Mitigated by the long deprecation window (the entire 1.x line) and by the
  CHANGELOG entry.

### Migration

The deprecated method keeps working unchanged for the entire 1.x line. Users
have three paths:

1. **Spring Boot starter users**: no action required. The starter wires
   `MicrometerMetricsRecorder` through `MicrometerBindings` and never calls
   `metricsRegistry(Object)`.
2. **Manual builder users on Micrometer**: replace
   `.metricsRegistry(meterRegistry)` with
   `.metricsRecorder(new MicrometerMetricsRecorder(meterRegistry))`. The
   Javadoc on the deprecated method shows the exact before/after.
3. **Custom recorder users**: already on `metricsRecorder(...)`; nothing to
   change.

The deprecation Javadoc, the `docs/integrations.md` section, and the CHANGELOG
entry for the 1.x release that lands PR 10 all carry the same migration
snippet, so the fix is discoverable from any of the surfaces a user is likely
to read.

## Alternatives considered

1. **Remove immediately in 1.x.** Rejected. Breaks any consumer outside the
   Spring Boot starter who is currently passing a registry through the
   reflective bridge. The point of a 1.x line is to keep the source-compat
   promise; an immediate removal violates it.
2. **Keep the reflective method and only fix the fragility** (e.g. cache the
   `Constructor`, narrow the classloader lookup, drop `setAccessible`).
   Rejected. The underlying problem is the `Object` parameter and the
   classloader split, not the implementation details of the reflective call.
   Hardening the bridge does not remove the type-safety hole that A-04 calls
   out.
3. **Introduce a parallel typed method without deprecating the old one.**
   Rejected. Two equivalent entry points with different ergonomics is a worse
   API than one entry point with a clear deprecation arrow. Users who copy
   examples would split between the two indefinitely.

## References

- Hallazgos: A-04 (reflective `metricsRegistry(Object)` is type-unsafe and
  classloader-fragile).
- PR 10 — `core: deprecar metricsRegistry(Object) reflexivo` (see
  `data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md`).
- ADR draft: `data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md`,
  section "ADR-4 — Metrics integration: drop reflective bridge".
- Related ADRs: none directly. ADR-3 ("Threading policy") touches metrics
  recording from the poll listener but does not interact with this builder
  surface.
- Open: confirm during PR 10 whether `RemoteFlagProviderBuilder` exposes an
  equivalent reflective method that needs the same treatment.
