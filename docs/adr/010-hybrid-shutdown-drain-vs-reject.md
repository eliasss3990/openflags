# 10. Hybrid shutdown semantics — drain final snapshot, then reject

## Status

Accepted — 2026-05-08

## Context

`HybridFlagProvider` writes snapshots off the poller thread (ADR-3) through a
single-threaded executor. On `shutdown()`, three things race:

1. The poller may produce one last snapshot *after* `remote.shutdown()` returns
   but *before* the snapshot executor stops accepting tasks.
2. A writer task may already be in flight, holding the latest value pulled via
   `pendingSnapshot.getAndSet(null)`.
3. `pendingSnapshot` may hold a value that was enqueued but not yet picked up.

Without an explicit policy, any of these can be silently dropped — the user
restarts the process and the local snapshot is staler than the last successful
poll. The bug is invisible until cold start.

We have two options:

- **Reject-on-shutdown.** Stop the executor immediately, drop pending work.
  Simpler, but loses the freshest snapshot in the common case.
- **Drain-then-reject.** Synchronously persist whatever is pending on the
  shutdown thread, then stop the executor. Costs one extra write at shutdown.

## Decision

`HybridFlagProvider.shutdown()` performs a **best-effort final drain on the
shutdown thread**, then stops the snapshot executor with a bounded await:

1. Shutdown the remote provider (stops further `onPollComplete` callbacks).
2. Shutdown the file provider.
3. Pop `pendingSnapshot.getAndSet(null)` and, if non-null, write it
   synchronously on the caller thread via `writeSafe(...)`. This runs *after*
   the providers are shut down, so no new value can arrive concurrently.
4. If we own the executor, `shutdown()` it and wait up to **2 seconds** with
   `awaitTermination`. On timeout, `shutdownNow()` interrupts the in-flight
   writer.
5. Clear public listeners.

The drain in step 3 is synchronous and runs *outside* the executor, so it
cannot deadlock against a `RejectedExecutionException` from the executor's own
shutdown. Any value that was already in flight inside the executor is allowed
to complete naturally during the 2s await window.

Rationale for the 2s budget: a snapshot write is a small JSON serialize plus
an atomic rename. On healthy filesystems it completes in single-digit ms;
2s gives an order of magnitude headroom for transient I/O contention without
hanging shutdown indefinitely. If the disk is genuinely stuck,
`shutdownNow()` interrupts and the next cold start falls back to whatever
snapshot was persisted previously.

`enqueueSnapshotWrite` and `drainAndWriteSafe` already handle
`RejectedExecutionException` from racing the executor's shutdown — they clear
`pendingSnapshot` and log at DEBUG. This means a poll completing concurrently
with shutdown is allowed to lose its snapshot, since the synchronous drain in
step 3 already covered the latest value visible at the moment shutdown was
called.

## Consequences

- Cold-start consumers see the freshest snapshot the poller was able to
  produce before shutdown was invoked, with one exception: a poll that
  completes *between* step 3 and the executor finishing termination is
  dropped. That window is bounded by the 2s timeout and is the explicit
  trade-off against unbounded shutdown latency.
- Shutdown is bounded: at most 2s of executor await plus the cost of one
  synchronous final write.
- Callers that need stronger guarantees (no dropped polls during shutdown)
  must stop the source of poll events themselves before calling
  `shutdown()`. The provider does not expose hooks for this today; if a real
  use case appears, we can add a quiesce step.
