# Providers

A `FlagProvider` is the source of flag data. openflags ships with two providers and defines a contract for building custom ones.

---

## FileFlagProvider

Reads flag definitions from a YAML or JSON file on the local filesystem.

```java
FileFlagProvider provider = FileFlagProvider.builder()
        .path(Path.of("/etc/myapp/flags.yml"))
        .watchEnabled(true)
        .build();
```

| Builder method | Type | Default | Description |
|---|---|---|---|
| `path(Path)` | `Path` | required | Absolute or relative path to the flag file. |
| `path(String)` | `String` | required | Same as above, parsed via `Paths.get()`. |
| `watchEnabled(boolean)` | `boolean` | `true` | Enable hot reload via `WatchService`. |

**Thread safety:** flag reads use an `AtomicReference` swap; listeners use `CopyOnWriteArrayList`. Thread-safe for concurrent reads and a single writer (the `FileWatcher` callback thread).

**Error handling:** if the file cannot be parsed during reload (e.g. mid-write), the previous flags are retained and the provider state transitions to `ERROR`. On the next successful reload, state returns to `READY`.

---

## InMemoryFlagProvider

In-memory provider for testing. No files, no I/O.

```java
InMemoryFlagProvider provider = new InMemoryFlagProvider();

OpenFlagsClient client = OpenFlagsClient.builder()
        .provider(provider
            .setBoolean("feature-x", true)
            .setString("variant", "control"))
        .build();

// Modify flags at any time — listeners are notified
provider.setBoolean("feature-x", false);
provider.setDisabled("variant");
provider.remove("feature-x");
provider.clear();
```

Available mutation methods: `setBoolean`, `setString`, `setNumber`, `setObject`, `setDisabled`, `remove`, `clear`. All return `this` for chaining.

---

## Implementing a custom provider

Implement the `FlagProvider` interface from `openflags-core`:

```java
public class MyCustomProvider implements FlagProvider {

    @Override
    public void init() {
        // Load initial flag data. Must be idempotent.
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        // Return the flag or Optional.empty() if not found.
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        // Return an unmodifiable snapshot.
    }

    @Override
    public ProviderState getState() {
        // NOT_READY, READY, ERROR, or STALE.
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) { ... }

    @Override
    public void removeChangeListener(FlagChangeListener listener) { ... }

    @Override
    public void shutdown() {
        // Release resources. Must be idempotent.
    }
}
```

Then wire it into the client:

```java
OpenFlagsClient client = OpenFlagsClient.builder()
        .provider(new MyCustomProvider())
        .build();
```

In Spring Boot, declare both a `FlagProvider` bean and an `OpenFlagsClient` bean. The auto-configuration backs off from creating `OpenFlagsClient` when a bean of that type is already present (`@ConditionalOnMissingBean`), but it will still attempt to create `fileFlagProvider` unless you also suppress it:

```java
@Configuration
public class MyFlagsConfig {

    @Bean
    public FlagProvider flagProvider() {
        return new MyCustomProvider();
    }

    @Bean
    public OpenFlagsClient openFlagsClient(FlagProvider flagProvider) {
        return OpenFlagsClient.builder().provider(flagProvider).build();
    }
}
```

Alternatively, set `openflags.provider=none` (Phase 2) to disable the file provider bean entirely.

---

## Provider lifecycle

All providers follow the same lifecycle contract:

1. **Create** — via constructor or builder. No I/O at this stage.
2. **`init()`** — loads initial data; blocks until ready or throws. Idempotent.
3. **Evaluate** — `getFlag()` and `getAllFlags()` may be called concurrently.
4. **`shutdown()`** — releases all resources (threads, file handles, connections). Idempotent. After shutdown, evaluation methods throw `IllegalStateException`.

`OpenFlagsClientBuilder.build()` calls `init()` automatically.
