# 9. Hybrid provider metrics

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

`HybridFlagProvider` composes a primary `RemoteFlagProvider` with a secondary
`FileFlagProvider` that serves a locally persisted snapshot. The contract is
operational: when the remote source is reachable and healthy, evaluations go
through the primary; when the primary is degraded or in error, evaluations
transparently fall back to the snapshot. The snapshot is itself refreshed by
the primary's poll loop (see ADR-3).

The starter (`OpenFlagsAutoConfiguration`) already wires a
`MetricsRecordingPollListener` for the *pure remote* mode at
`OpenFlagsAutoConfiguration:126`. In *hybrid* mode (lines 179-182) only
`hybridProvider.setMetricsRecorder(recorder)` is called, which is a different
extension point and does not produce the `openflags.poll.*` counters/timers.
Finding **N-08** captured this gap: dashboards built on `openflags.poll.success`
or `openflags.poll.failure` look empty when the deployment runs in hybrid mode,
even though polling is happening underneath.

The gap is not limited to poll metrics. Today the operator has **no signal at
all** for the events that are unique to hybrid mode:

- When does the provider actually fall back to the snapshot? How often?
- For how long does each fallback episode last?
- How much extra latency does fallback evaluation add (or save)?
- Is the primary's local cache hot? What is its hit rate?
- How frequently does the underlying `ProviderState` transition between
  `HEALTHY`, `DEGRADED`, `ERROR`?

Without these signals, hybrid mode behaves like a black box: it "just works"
until it does not, and then post-mortems rely on logs alone. Logs are useful
but not aggregable; rate, percentile, and trend questions require metrics.

PR 15 closes the polling-metrics gap (parity with the pure remote mode) and is
also the natural place to add the hybrid-specific metrics described below.
This ADR defines the surface of those metrics so PR 15 has a contract to
implement.

## Decision

`HybridFlagProvider` will expose the following metrics through the existing
`MetricsRecorder` SPI (and therefore through `MicrometerMetricsRecorder` when
Micrometer is on the classpath). The names below are **proposed names,
finalised in PR 15**: minor renames are allowed during implementation as long
as the prefix `openflags.hybrid.*` and the conventions in ADR-4 are kept.

### 1. Poll metrics (parity with remote mode)

PR 15's primary goal. The same counters/timers already produced by
`MetricsRecordingPollListener` in pure remote mode must be produced when the
provider is wrapped in hybrid:

- `openflags.poll.success` — counter, increments on each successful poll.
- `openflags.poll.failure` — counter, increments on each failed poll, tagged
  with `reason` (`timeout`, `http_error`, `parse_error`, `other`).
- `openflags.poll.latency` — timer, distribution of poll round-trip durations.

These are the existing names. No new naming decision is needed; the decision
is structural: hybrid must compose its internal listener with a
`MetricsRecordingPollListener` so that both the snapshot-writing logic and the
metrics emission run on every poll. ADR-2 (lifecycle) and ADR-3 (threading)
make this composition straightforward: the metrics listener runs on the poller
thread, the snapshot listener dispatches to the snapshot executor.

### 2. Fallback activation metrics (hybrid-specific)

- `openflags.hybrid.fallback.activations` — counter, increments **once** per
  transition from "evaluating against primary" to "evaluating against
  fallback". Tagged with `cause` (`primary_unhealthy`, `primary_error`,
  `primary_not_ready`).
- `openflags.hybrid.fallback.deactivations` — counter, increments once per
  transition back to primary.
- `openflags.hybrid.fallback.active` — gauge, `1` while the provider is
  serving from fallback, `0` otherwise. Allows alerting on sustained fallback
  episodes.
- `openflags.hybrid.fallback.duration` — timer, records the duration of each
  completed fallback episode (start at activation, stop at deactivation).
  Episodes still open at shutdown are not recorded; they are implicit in the
  active gauge's last sample.

The activation/deactivation counters give rate; the gauge gives instantaneous
state; the timer gives episode-length distribution. Together they let an
operator answer "are we currently degraded?", "how often do we degrade?", and
"how long do degradations last?" without parsing logs.

### 3. Latency metrics (per source)

- `openflags.hybrid.evaluation.latency` — timer, tagged with
  `source=primary|fallback`. Records the wall-clock time spent inside
  `evaluate()` per source. Cardinality is bounded (two values).

This metric is intentionally narrow: it does not include `flag_key` to keep
cardinality bounded. Per-flag latency, if ever needed, belongs to
`openflags.evaluation.*` and not to the hybrid layer.

### 4. Primary cache hit/miss

`RemoteFlagProvider` already maintains an in-memory cache of the last known
flag set (the snapshot it polls into). Hybrid evaluation that hits this cache
(no fallback involved, no remote round-trip) is the happy path. Cases where
the primary's cache is cold or stale and must be repopulated before the
evaluation can resolve are the slow path and are worth observing:

- `openflags.hybrid.primary.cache.hits` — counter.
- `openflags.hybrid.primary.cache.misses` — counter.

These are *evaluation-time* hit/miss counters, not poll-time. They reflect
whether `evaluate()` found a usable value in the primary's last good snapshot
or had to wait/fall back.

### 5. Provider state transitions

- `openflags.hybrid.state.transitions` — counter, tagged with
  `from=<state>` and `to=<state>` where `<state>` is one of `HEALTHY`,
  `DEGRADED`, `ERROR`, `NOT_READY`, `SHUTDOWN` (the `ProviderState` values
  surviving ADR-6's deprecation of `STALE`).
- `openflags.hybrid.state.current` — gauge, exposed as a numeric encoding of
  the current `ProviderState` (e.g. `HEALTHY=0`, `DEGRADED=1`, `ERROR=2`,
  `NOT_READY=3`, `SHUTDOWN=4`). Allows building "current state" panels
  without having to scrape the actuator endpoint.

Tag cardinality for the transitions counter is bounded by the cartesian
product of states, which is small (~25 combinations, most of them
unreachable). No `flag_key` or per-instance tag is added.

### 6. Tag conventions

Following ADR-4, all metrics are emitted through `MetricsRecorder`. Tags
common to every hybrid metric:

- `provider=hybrid` — distinguishes from pure remote when both modes coexist
  (rare, but possible in tests). Tagging is applied by
  `MicrometerMetricsRecorder`, not by `HybridFlagProvider`, so user code
  passing a custom `MetricsRecorder` decides whether to add it.

No instance-id tag is emitted by default. If a deployment runs multiple
`OpenFlagsClient` instances against different remotes and wants to
disambiguate, it should add the tag at the `MeterRegistry` level via a
`MeterFilter`.

### 7. Naming conventions

All names follow the existing convention used by ADR-4 and the current
`MicrometerMetricsRecorder`:

- prefix `openflags.`
- subsystem segment (`hybrid`, `poll`, `evaluation`)
- noun describing the measurement (`activations`, `latency`, `hits`)
- units are NOT encoded in the name; Micrometer infers them from the meter
  type.

## Consequences

### Positive

- Operators get the same poll-metrics dashboard in hybrid mode that they
  already use in pure remote mode (closes N-08).
- Fallback episodes are observable in real time and aggregable across
  instances.
- State transitions can drive alerts (e.g. "more than 3 transitions to
  ERROR per 5 minutes").
- Per-source evaluation latency exposes whether fallback is "free" (snapshot
  in memory) or "expensive" (snapshot reread).
- All extension is additive: existing consumers see new metrics appear, never
  see existing metrics change shape.

### Negative / Risks

- **Cardinality**: the transition counter has a bounded but non-trivial tag
  set. Worst case ~25 series. Acceptable. If a future state is added (none
  planned for 1.x; ADR-7 may introduce some in 2.0), the cartesian product
  grows; this ADR's recommendation is to keep `from`/`to` as the only tags
  and accept the bound.
- **Hot path overhead**: `evaluate()` is on the hot path. Adding two
  hit/miss counters and a timer on every call adds work. Micrometer's
  counters and timers are designed for this and the cost is sub-microsecond
  in practice, but the counters must be looked up once at construction
  (cached in fields) and not on each call. PR 15 must verify this.
- **Gauge lifecycle**: `openflags.hybrid.fallback.active` and
  `openflags.hybrid.state.current` are gauges with weak references in
  Micrometer. They must be registered through `MicrometerMetricsRecorder` in
  a way that survives the provider's lifecycle. Tested in PR 15.
- **Tag explosion via user code**: if a custom `MetricsRecorder` adds a
  high-cardinality tag (e.g. `flag_key`, `tenant_id`), the contract is
  broken. This ADR documents the expected cardinality envelope and the
  recorder is responsible for honouring it.

### Migration

- Metrics are opt-in via Micrometer. A deployment without Micrometer on the
  classpath sees no behavioural change; `MetricsRecorder` defaults to no-op
  and the counters are never created. The
  `NoMicrometerClasspathTest` already covers this path and stays green.
- For deployments already on Micrometer, the new meters appear after the
  upgrade. No name collides with existing meters; existing dashboards keep
  working.
- The `MetricsRecorder` SPI itself does not change in this ADR. The new
  emissions are produced through the existing methods on the recorder
  (counters/timers/gauges). PR 15 may need to extend the recorder with a
  thin convenience method for the gauge, but the SPI extension is additive.

## Alternatives considered

### Logs only

Emit structured log lines for fallback activation/deactivation and state
transitions; do not expose metrics. Rejected because:

- logs are not aggregable into rate or percentile views without a separate
  pipeline (ELK, Loki) that not every consumer runs;
- alerting on "fallback active for more than 30 seconds" is trivial with a
  gauge and impossible without one short of a stateful log processor;
- the project already integrates Micrometer for poll metrics; adding hybrid
  metrics is consistent.

Logs are still emitted for the same events at INFO/WARN; metrics are
complementary, not a replacement.

### Single fallback gauge, no counters

Expose only `openflags.hybrid.fallback.active` and skip the activation /
deactivation counters. Rejected because:

- a gauge alone cannot answer "how often" (only "is it now");
- counters are cheap and the cardinality is fixed.

### Per-flag fallback metrics

Tag fallback metrics with `flag_key` so that "which flags caused the
fallback" is observable. Rejected because fallback is provider-level: the
primary as a whole is unhealthy, not a single flag. Per-flag tagging would
inflate cardinality with no extra information.

### Alternative names: `openflags.provider.hybrid.*`

Considered nesting under `provider`. Rejected because the existing
poll/evaluation metrics live at `openflags.poll.*` and `openflags.evaluation.*`
without a `provider` segment, and consistency wins over hierarchy.

### Push state via actuator only

Surface state through `OpenFlagsHealthIndicator` and let the operator scrape
the actuator endpoint. Rejected because the actuator gives a snapshot, not a
time series; transitions between scrapes are invisible.

## References

- PR 15 — `hybrid: cablear MetricsRecordingPollListener tambien en hybrid`
  (plan in `/data/proyectos/openflags/main/plan/code-review-arquitectura/05-plan-ejecucion.md`).
- Finding N-08 — gap of poll metrics in hybrid mode
  (`/data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md`).
- ADR-2 — Provider lifecycle and configuration phase
  (`docs/adr/002-flag-provider-lifecycle.md`). Constrains where the metrics
  listener is registered.
- ADR-3 — Hybrid snapshot writes off the poller thread
  (`docs/adr/003-snapshot-write-off-poller-thread.md`). Establishes the
  threading model the metrics listener composes with.
- ADR-4 — Metrics integration: drop reflective bridge (forthcoming).
  Establishes the `MetricsRecorder` SPI and the `metricsRegistry(Object)`
  deprecation. The metrics defined here are emitted through that SPI.
- ADR-6 — `ProviderState` slim down: drop unused `STALE` (forthcoming).
  Bounds the tag set for `openflags.hybrid.state.transitions`.
