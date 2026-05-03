# 3. Hybrid snapshot writes off the poller thread

## Status

Accepted — 2026-05-03

## Context

`HybridFlagProvider` polls a remote source on a fixed cadence and persists each
fresh result to a local snapshot file so that the next cold start can serve
flags before the network is reachable. Today the snapshot write happens
synchronously on the same thread that drives the poll loop.

This coupling has three concrete problems:

1. **A-05 — write latency leaks into the poll cadence.** A slow disk, a busy
   filesystem, or an `fsync` stall blocks the poller. Subsequent ticks drift,
   are skipped, or queue up, depending on the scheduler. The user-configured
   `pollInterval` becomes a lower bound rather than a target.
2. **N-03 — `FileWatcher` race on the snapshot file.** When the snapshot file
   is also being watched (e.g. another `FileFlagProvider` or an external
   process), partial writes from the poller thread can be observed mid-flush.
   Pushing the write off-thread does not by itself fix atomicity, but it lets
   us serialize all writes through a single dedicated writer where the
   atomic-rename strategy is owned end to end.
3. **Undocumented thread policy.** The library currently spins up an internal
   scheduler for polling, invokes `EvaluationListener` and
   `FlagChangeListener` callbacks inline on whichever thread triggered them,
   and performs I/O on the same poller thread. None of this is documented, so
   integrators cannot reason about reentrancy, blocking, or shutdown.

We need a decision that (a) decouples snapshot persistence from the poll
cadence, (b) gives users a single coalesced view of "the latest snapshot to
write", and (c) makes the global threading model explicit.

## Decision

### 1. Dedicated snapshot executor

`HybridFlagProvider` gains a new collaborator:

```java
private final Executor snapshotExecutor;
private final AtomicReference<Map<String, Flag>> pendingSnapshot =
        new AtomicReference<>();
```

The default executor is a single-threaded executor with a named thread:

```java
Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "openflags-snapshot-writer");
    t.setDaemon(true);
    return t;
});
```

The builder exposes an optional override:

```java
HybridFlagProvider.builder()
        .snapshotExecutor(myExecutor) // optional
        .build();
```

When the user supplies their own `Executor`, they own its lifecycle. When the
default is used, `HybridFlagProvider#close()` shuts it down with a bounded
await.

### 2. Coalescing via `AtomicReference`

After every successful poll, the poller thread does the following and returns
immediately:

```java
void onPollSuccess(Map<String, Flag> fresh) {
    Map<String, Flag> previous = pendingSnapshot.getAndSet(fresh);
    if (previous == null) {
        snapshotExecutor.execute(this::drainAndWrite);
    }
    // else: a writer task is already in flight or queued; it will pick up
    // the freshest value when it next reads pendingSnapshot.
}

private void drainAndWrite() {
    Map<String, Flag> snapshot = pendingSnapshot.getAndSet(null);
    if (snapshot == null) {
        return;
    }
    try {
        snapshotStore.writeAtomic(snapshot);
    } catch (IOException e) {
        log.warn("snapshot write failed", e);
    }
    // If a newer snapshot arrived while we were writing, re-arm.
    if (pendingSnapshot.get() != null) {
        snapshotExecutor.execute(this::drainAndWrite);
    }
}
```

Properties of this scheme:

- The poller thread never blocks on disk I/O.
- At most one writer task is queued at a time. Bursty polls collapse into a
  single write of the latest value (coalescing). Older intermediate snapshots
  are dropped on purpose; only the most recent state matters for cold start.
- `pendingSnapshot.getAndSet(null)` is the linearization point. Anything that
  arrives after it triggers a fresh re-arm via the `pendingSnapshot.get()`
  check at the tail of `drainAndWrite`.

### 3. Global thread policy, documented

We adopt and document the following policy across all `FlagProvider`
implementations:

- **Polling / refresh threads** are internal to each provider. They are daemon
  threads with a stable, prefixed name (`openflags-...`) and must not be
  assumed by callers to be the same thread that invokes user APIs.
- **Snapshot writes** for `HybridFlagProvider` run on `snapshotExecutor`
  (default: `openflags-snapshot-writer`).
- **`EvaluationListener` callbacks** are invoked synchronously on the thread
  that calls `isEnabled` / `getValue`. Listeners must be non-blocking and
  thread-safe; the library makes no ordering or thread-confinement guarantees
  beyond "happens-after the evaluation it describes".
- **`FlagChangeListener` callbacks** are invoked on the provider's internal
  refresh thread (for `HybridFlagProvider`, the poller thread; for
  `FileFlagProvider`, the file-watcher thread). They must be non-blocking;
  long work must be dispatched to a user-owned executor.
- **User-supplied executors** are never shut down by the library.

This policy is reflected in:

- The Javadoc of `FlagProvider`, `EvaluationListener` and
  `FlagChangeListener`.
- A new `docs/threading.md` document that aggregates the policy in one place
  and is linked from the Javadoc.

## Consequences

### Positive

- Poll cadence is no longer perturbed by disk latency. The observable
  `pollInterval` matches the configured value within scheduler jitter.
- Snapshot writes are serialized through a single writer, which is the
  natural place to own atomic-rename semantics and close the `FileWatcher`
  race surfaced in N-03.
- The threading contract becomes part of the public API. Integrators can
  reason about reentrancy and decide where to push their own work.
- Bursty refreshes (e.g. on reconnect) collapse to a single write instead of
  N back-to-back writes.

### Negative / costs

- **Shutdown is more complex.** `HybridFlagProvider#close()` must now stop
  the poller, drain `pendingSnapshot` (best-effort final write), then
  shut down `snapshotExecutor` with a bounded `awaitTermination`. The
  default path owns the executor; the user-supplied path does not.
- **User-owned `Executor` lifecycle.** Callers who pass a custom executor
  are responsible for keeping it alive for as long as the provider is in
  use, and for shutting it down afterwards. This is documented in the
  builder Javadoc.
- **Lost intermediate snapshots are intentional.** A user who expects every
  successful poll to land on disk will be surprised. The Javadoc states
  explicitly that only the latest snapshot is persisted.
- **Failure visibility shifts.** A write failure no longer surfaces on the
  poller thread. We log at `WARN` and expose it through the existing
  provider state / health hooks; we do not introduce a new exception type.

### Neutral

- Memory footprint grows by one reference (`pendingSnapshot`) plus the
  retained `Map<String, Flag>` until the writer drains it. This is bounded
  by the size of the flag set and is negligible in practice.

## Alternatives considered

### A. `CompletableFuture.runAsync(this::write)` on the common pool

Rejected. `ForkJoinPool.commonPool` is a shared, application-wide resource.
Blocking I/O on it is an anti-pattern: it can starve other users of the
pool, and its sizing is tuned for CPU-bound work. It also gives us no
natural place to hang coalescing or shutdown.

### B. Synchronous write with a timeout

Rejected. A `Future#get(timeout)` from the poller thread bounds the worst
case but does not solve the underlying problem: a write that consistently
takes longer than the timeout will cause every poll to either block for
the full timeout or skip the snapshot entirely. It also does not address
the `FileWatcher` race, since concurrent timed-out writes can still
overlap.

### C. Per-poll thread (fire-and-forget `new Thread(...).start()`)

Rejected implicitly. Unbounded thread creation under burst conditions,
no coalescing, no shutdown story.

## References

- `/data/proyectos/openflags/main/plan/code-review-arquitectura/06-adrs-pendientes.md` — ADR-3 source
- A-05 — poll cadence drift caused by synchronous snapshot write
- N-03 — `FileWatcher` race on snapshot file
- Plan PR 9 — Hybrid snapshot off-thread + threading docs
- `docs/threading.md` (new, introduced by this ADR)
