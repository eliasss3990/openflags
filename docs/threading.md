# Threading model

This page describes the threads that the openflags library creates and uses,
and the contract for callbacks invoked on user-supplied code. It is the
single normative reference for reentrancy, blocking, and shutdown questions.
See ADR-3 (`docs/adr/003-snapshot-write-off-poller-thread.md`) for the design
decision that established this contract.

## Thread inventory

| Thread name | Owner | Daemon | Purpose |
| --- | --- | --- | --- |
| `openflags-remote-poller-*` | `RemoteFlagProvider` | yes | Periodic remote refresh. |
| `openflags-file-watcher-*` | `FileFlagProvider` | yes | `WatchService` events for snapshot files. |
| `openflags-snapshot-writer` | `HybridFlagProvider` (default executor) | yes | Serializes snapshot writes off the poller thread. |
| user-supplied executor | caller | n/a | Used when the caller passes `HybridFlagProviderBuilder.snapshotExecutor(...)`. |

All library-owned threads are daemons with a stable, prefixed name
(`openflags-...`). Callers must not assume any of these threads is the same
thread that invokes the public API.

## Callback contract

### `EvaluationListener`
- Invoked **synchronously** on the thread that calls `isEnabled` /
  `getValue` / `getFlag`.
- Must be non-blocking and thread-safe.
- The library makes no ordering or thread-confinement guarantees beyond
  "happens-after the evaluation it describes".

### `FlagChangeListener`
- Invoked on the provider's internal refresh thread:
  - `RemoteFlagProvider` and `HybridFlagProvider` (remote source): the
    `openflags-remote-poller-*` thread.
  - `FileFlagProvider` and `HybridFlagProvider` (file source): the
    `openflags-file-watcher-*` thread.
- Must be non-blocking. Long work must be dispatched to a user-owned
  executor by the listener itself.
- Exceptions thrown by listeners are caught and logged at `WARN`; they do
  not interrupt change propagation to other listeners.

## Snapshot write thread (Hybrid)

`HybridFlagProvider` writes snapshots on a dedicated executor instead of the
poller thread. Properties:

- The poller thread never blocks on disk I/O. Each successful poll publishes
  the latest snapshot via `AtomicReference.getAndSet(...)` and returns.
- At most one writer task is queued at a time. Bursty polls collapse into a
  single write of the latest value (coalescing). Older intermediate
  snapshots are dropped on purpose; only the most recent state matters for
  cold start.
- A write failure is logged at `WARN` and surfaced through diagnostics
  (`hybrid.last_snapshot_write`). It is not propagated as an exception to
  the poll caller, since the poll already returned by the time the write
  fails.

### Default executor

When no executor is supplied, the provider creates a
`single-threaded daemon executor` named `openflags-snapshot-writer` and
shuts it down on `HybridFlagProvider#shutdown()` with a bounded
`awaitTermination` (currently 2 seconds). After shutdown returns, no
further writes are dispatched.

### User-supplied executor

```java
HybridFlagProvider provider = HybridFlagProvider.builder()
        .remoteConfig(remoteConfig)
        .snapshotPath(snapshotPath)
        .snapshotExecutor(myExecutor) // optional
        .build();
```

When the caller supplies an `Executor`:

- The caller owns its lifecycle. `HybridFlagProvider#shutdown()` will not
  shut it down.
- The caller is responsible for keeping it alive for as long as the
  provider is in use.
- The caller is responsible for shutting it down afterwards.
- `Runnable::run` is supported (synchronous in-thread execution) and is
  useful in tests.

## Shutdown

`HybridFlagProvider#shutdown()` runs in this order:

1. Stop the remote poller (`remote.shutdown()`) — no new
   `onPollComplete` will be delivered.
2. Stop the file watcher (`file.shutdown()`).
3. Drain `pendingSnapshot` synchronously on the shutdown thread
   (best-effort final write). This handles the case where a poll happened
   between the listener stopping and the executor stopping.
4. If the executor is owned by the provider, `shutdown()` it and
   `awaitTermination(2s)`. On timeout or interruption, fall back to
   `shutdownNow()`. User-supplied executors are not touched.

After `shutdown()` returns, `getFlag(...)` throws `IllegalStateException`.
