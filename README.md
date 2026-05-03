# openflags

[![CI](https://github.com/eliasss3990/openflags/actions/workflows/ci.yml/badge.svg)](https://github.com/eliasss3990/openflags/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://adoptium.net/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.eliasss3990/openflags-core.svg?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.eliasss3990)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Lightweight, SDK-first feature flag library for Java and Spring Boot.

Evaluate boolean, string, number, and object flags from a local YAML or JSON file, an HTTP backend, or a hybrid setup that combines remote sync with a local fallback. Hot reload detects file changes at runtime without restarting the application, and the provider model is extensible — implement `FlagProvider` to plug in any backend.

## Documentation

- [Getting started](docs/getting-started.md) — end-to-end walk-through
- [Observability](docs/observability.md) — metrics catalog, MDC keys, dashboard queries
- [Upgrade guide](docs/upgrade-guide.md) — versioning policy and migration steps
- [Examples](examples/) — runnable Spring Boot quickstart
- [Benchmarks](openflags-benchmarks/) — JMH harness for the evaluation hot path
- [Contributing](CONTRIBUTING.md) — how to set up, branch, and submit PRs
- [Security](SECURITY.md) — private vulnerability disclosure
- [Releasing](RELEASING.md) — maintainer-only release procedure

---

## Features

- **Type-safe evaluation** — `getBooleanValue`, `getStringValue`, `getNumberValue`, `getObjectValue`
- **File-based provider** — YAML and JSON, configurable path
- **Hot reload** — automatic reload on file change via `WatchService`, with debounce and mid-write retry
- **Spring Boot auto-configuration** — zero-config setup via `openflags-spring-boot-starter`
- **Remote and hybrid providers** — HTTP polling with circuit breaker, optional local snapshot for offline resilience
- **Spring Actuator integration** — `/actuator/health` reports provider state and provider-specific diagnostics when Actuator is on the classpath
- **Metrics with Micrometer** — opt-in adapter that exposes evaluations, polls and routing fallbacks (no Micrometer dependency required if unused)
- **Evaluation listeners** — synchronous hook for audit, tracing and custom telemetry
- **In-memory provider** — for testing without files
- **Extensible** — implement `FlagProvider` to plug in any backend

---

## Requirements

- Java 21 or later
- Maven 3.9+
- Spring Boot 3.x (for the starter; core is framework-agnostic)

---

## Quick start

### Standalone (no Spring Boot)

**1. Add the dependency**

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-provider-file</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

**2. Create a flag file** (`flags.yml`)

```yaml
flags:
  dark-mode:
    type: boolean
    value: true
    description: "Enables dark mode UI"

  welcome-message:
    type: string
    value: "Hello, world!"

  rollout-rate:
    type: number
    value: 0.25

  feature-config:
    type: object
    value:
      timeout: 30
      retries: 3
```

**3. Evaluate flags**

```java
FileFlagProvider provider = FileFlagProvider.builder()
        .path(Path.of("flags.yml"))
        .watchEnabled(true)   // hot reload
        .build();

OpenFlagsClient client = OpenFlagsClient.builder()
        .provider(provider)
        .build();

boolean darkMode = client.getBooleanValue("dark-mode", false);
String message  = client.getStringValue("welcome-message", "Hi");
double rate     = client.getNumberValue("rollout-rate", 0.0);

// Full result with resolution reason
EvaluationResult<Boolean> result = client.getBooleanResult("dark-mode", false, EvaluationContext.empty());
// result.value()  → true
// result.reason() → RESOLVED

client.shutdown();
```

---

### Spring Boot

**1. Add the starter**

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-spring-boot-starter</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

**2. Place `flags.yml` in `src/main/resources`** (or configure a custom path)

**3. Inject and use**

```java
@Service
public class FeatureService {

    private final OpenFlagsClient flags;

    public FeatureService(OpenFlagsClient flags) {
        this.flags = flags;
    }

    public boolean isDarkModeEnabled() {
        return flags.getBooleanValue("dark-mode", false);
    }
}
```

**4. Configuration** (`application.yml`)

```yaml
openflags:
  provider: file                        # file (default) | remote | hybrid
  file:
    path: classpath:flags.yml           # supports classpath: and file: prefixes
    watch-enabled: true                 # hot reload (auto-disabled for files inside JARs)
```

---

## Targeting rules

Conditional evaluation is supported via rules declared directly in the flag file. Rules are evaluated in order; the first match wins.

### TargetingRule — attribute-based targeting

Return a specific value when user/context attributes match a set of conditions:

```yaml
flags:
  new-checkout:
    type: boolean
    value: false
    description: "New checkout flow"
    rules:
      - name: argentina-users
        kind: targeting
        value: true
        when:
          - attribute: country
            operator: EQ
            value: "AR"
```

### SplitRule — percentage rollout

Roll out a flag to a percentage of users using consistent hashing (same user always gets the same result):

```yaml
flags:
  new-dashboard:
    type: boolean
    value: false
    description: "New dashboard UI"
    rules:
      - name: 20pct-rollout
        kind: split
        value: true
        percentage: 20
```

### Building an EvaluationContext

Pass a `targetingKey` (stable user identifier) and any attributes you want to match against:

```java
EvaluationContext ctx = EvaluationContext.builder()
        .targetingKey("user-42")
        .attribute("country", "AR")
        .attribute("plan", "pro")
        .build();

EvaluationResult<Boolean> result = client.getBooleanResult("new-checkout", false, ctx);
// result.value()  → true   (matched argentina-users rule)
// result.reason() → TARGETING_MATCH
```

### EvaluationReason

| Reason | When |
|---|---|
| `RESOLVED` | Flag has no rules; the flag's static value was returned |
| `TARGETING_MATCH` | A `TargetingRule` matched the context attributes |
| `SPLIT` | A `SplitRule` matched based on bucket allocation |
| `VARIANT` | A `MultiVariantRule` matched and a weighted variant was selected |
| `NO_RULE_MATCHED` | Flag has rules but none matched; the flag's static value was returned |
| `FLAG_NOT_FOUND` | The flag key does not exist in the provider; the caller's default was returned |
| `FLAG_DISABLED` | The flag exists but is disabled; the caller's default was returned |
| `TYPE_MISMATCH` | The flag type does not match the requested type; the caller's default was returned |
| `PROVIDER_ERROR` | The provider failed during resolution; the caller's default was returned |

### Backward compatibility

Flags without a `rules:` section keep working as plain key-value lookups: the static `value` is returned with reason `RESOLVED`.

---

## Flag file format

```yaml
flags:
  <flag-key>:
    type: boolean | string | number | object
    value: <value matching the type>
    enabled: true                       # optional, defaults to true
    description: "..."                  # optional metadata
```

Supported formats: `.yml`, `.yaml`, `.json`.

An empty flag list is expressed as `flags: {}`.

---

## Testing

Use `InMemoryFlagProvider` to set up flags programmatically in tests — no files needed.

```java
InMemoryFlagProvider provider = new InMemoryFlagProvider();

OpenFlagsClient client = OpenFlagsClient.builder()
        .provider(provider
            .setBoolean("dark-mode", true)
            .setString("theme", "dark"))
        .build();

// Toggle flags mid-test
provider.setBoolean("dark-mode", false);

// Listen for changes
client.addChangeListener(event -> System.out.println("Changed: " + event.flagKey()));
```

Add the testing module to your test scope:

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-testing</artifactId>
    <version>0.5.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

---

## Modules

| Module | Description |
|---|---|
| `openflags-core` | Core SDK: `OpenFlagsClient`, `FlagProvider` interface, evaluation engine, observability SPIs |
| `openflags-provider-file` | File-based provider with YAML/JSON parsing and hot reload |
| `openflags-provider-remote` | HTTP-polling provider with circuit breaker and configurable backoff |
| `openflags-provider-hybrid` | Remote-primary, file-fallback provider with snapshot persistence |
| `openflags-spring-boot-starter` | Spring Boot auto-configuration, Actuator health indicator and Micrometer wiring |
| `openflags-testing` | `InMemoryFlagProvider` for unit and integration tests |
| `openflags-bom` | Bill of Materials for consistent dependency management |

---

## Building from source

```bash
git clone https://github.com/eliasss3990/openflags.git
cd openflags
mvn clean verify
```

Requires Java 21+ and Maven 3.9+.

---

## Roadmap

- Distributed-tracing helpers built on top of `EvaluationListener`
- Additional providers (HTTP push, message-bus subscription)
- Native-image / GraalVM reflection metadata bundle

---

## Variantes (MultiVariantRule)

Run A/B/n experiments by splitting traffic across multiple weighted variants. Weights must sum to 100; weight=0 temporarily disables a variant without changing user assignments.

```yaml
flags:
  checkout-experiment:
    type: string
    value: "control"
    enabled: true
    rules:
      - name: "abc-test"
        kind: multivariant
        variants:
          - value: "control"
            weight: 50
          - value: "treatment-a"
            weight: 25
          - value: "treatment-b"
            weight: 25
```

**Evaluation behavior:**
- With `targetingKey`: always selects a variant deterministically → `EvaluationReason.VARIANT`
- Without `targetingKey`: rule is skipped → `EvaluationReason.NO_RULE_MATCHED`

```java
EvaluationResult<String> result = client.getStringResult(
    "checkout-experiment", "control", EvaluationContext.of("user-123"));

// result.reason() == EvaluationReason.VARIANT
// result.value()  == "control" | "treatment-a" | "treatment-b"
```

> **Warning:** Reordering variants shifts bucket ranges and changes user assignments. Once an experiment is running, set an unwanted variant's weight to 0 rather than removing it.

---

## Remote provider

Fetch flags from an HTTP backend with automatic polling and stale-while-error cache.

### Standalone

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-provider-remote</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

```java
RemoteFlagProvider provider = RemoteFlagProviderBuilder
        .forUrl("https://flags.example.com")
        .bearerToken("my-api-token")
        .pollInterval(Duration.ofSeconds(30))
        .cacheTtl(Duration.ofMinutes(5))
        .build();

provider.init(); // initial fetch; throws ProviderException on failure
OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();
// ...
provider.shutdown(); // releases polling thread
```

### Spring Boot

```yaml
openflags:
  provider: remote
  remote:
    base-url: https://flags.example.com
    auth-header-name: Authorization
    auth-header-secret: "Bearer my-token"
    poll-interval: 30s
    cache-ttl: 5m
```

**State machine:** `NOT_READY → READY → DEGRADED → ERROR → SHUTDOWN`

- `DEGRADED`: a poll failed but cache TTL has not expired; stale data is served.
- `ERROR`: cache TTL exceeded; last known data is still served.

**Expected backend format** (same as file provider):

```json
{
  "flags": {
    "dark-mode": {
      "type": "boolean",
      "value": true,
      "enabled": true
    }
  }
}
```

---

## Hybrid provider

The hybrid provider combines a remote HTTP backend (primary) with a local snapshot file
(fallback). It is designed for production scenarios where you need resilience against backend
outages.

### When to use it

- You have a remote backend serving flags but need the application to start even when the backend
  is temporarily unavailable.
- You want automatic persistence of the latest remote state so a cold start with backend down
  can still serve meaningful data.

### Configuration (Spring Boot)

```yaml
openflags:
  provider: hybrid
  remote:
    base-url: https://flags.example.com
    poll-interval: 30s
    cache-ttl: 5m
  hybrid:
    snapshot-path: /var/lib/myapp/flags-snapshot.json
    snapshot-format: JSON          # or YAML
    watch-snapshot: true           # reload manually edited snapshot when remote is ERROR
    snapshot-debounce: 500ms       # ignore self-induced WatchService events
    fail-if-no-fallback: false     # allow startup even if both sources fail
```

### Guarantees

| Scenario | Behavior |
|---|---|
| Backend UP on cold start | READY; snapshot written after first poll |
| Backend DOWN on cold start, snapshot exists | DEGRADED; serves snapshot data |
| Backend DOWN on cold start, no snapshot | Throws `ProviderException` |
| Backend poll fails within cacheTtl | DEGRADED; continues serving cached remote data |
| Backend poll fails past cacheTtl | ERROR; routes to file provider |
| Backend recovers | Returns to READY; snapshot updated |
| Manual snapshot edit while remote is ERROR | Change event propagated to listeners |

### Snapshot atomicity

Every snapshot write uses a write-to-temp (UUID name) + `Files.move(ATOMIC_MOVE)` pattern so
concurrent readers never observe a partially written file. On filesystems that do not support
atomic move, the provider falls back to `REPLACE_EXISTING`.

---

## Observability

openflags ships with metrics, evaluation listeners, an extended health endpoint and a
circuit breaker on the remote provider. Every feature is zero-config when using the
Spring Boot starter and degrades gracefully when its optional dependencies are absent.

### Metrics with Micrometer

`micrometer-core` is an optional dependency of `openflags-core`. The starter wires a
`MicrometerMetricsRecorder` automatically when a `MeterRegistry` bean is present and
`openflags.metrics.enabled=true` (default). Without Micrometer or without a registry
bean the client uses a NOOP recorder; nothing else changes.

Counters and timers exposed (default tag set; `flag` and `variant` are added when
`tag-flag-key=true`):

- `openflags.evaluations.total{provider.type,type,reason}` — one increment per evaluation
- `openflags.evaluation.duration{provider.type,type,reason}` (timer) — per-evaluation latency
- `openflags.evaluations.errors.total{provider.type,error.type}` — evaluation errors (also tags `flag` when `tag-flag-key=true`)
- `openflags.evaluations.listener.errors.total{listener}` — listener that threw
- `openflags.poll.total{outcome}` — remote-provider poll counter
- `openflags.poll.duration{outcome}` (timer) — remote-provider poll latency
- `openflags.snapshot.writes.total{outcome}` / `openflags.snapshot.write.duration{outcome}` — hybrid snapshot writes
- `openflags.flag_changes.total{change_type}` — flag change events observed
- `openflags.hybrid.fallback.total{from,to}` — routing change in hybrid

The remote-provider circuit-breaker state and the file-watcher heartbeat are surfaced
through `ProviderDiagnostics` (and therefore the `/actuator/health` payload) as
`remote.circuit_open` and `file.watcher_alive` respectively. Gauges with those names
are not currently registered on the `MeterRegistry`.

Configuration:

```yaml
openflags:
  metrics:
    enabled: true            # set to false to disable the customizer
    tag-flag-key: true       # add a flag=<key> tag to per-flag counters
    tags:
      env: prod              # static tags applied via MeterFilter.commonTags
      region: eu-west-1
```

### EvaluationListener

Every bean that implements `EvaluationListener` is auto-detected and invoked after every
evaluation. Listeners are dispatched synchronously, in `@Order` order, with per-listener
exception isolation.

```java
@Bean
EvaluationListener auditListener() {
    return event -> {
        // event.flagKey, event.targetingKey, event.resolvedValue, event.reason, ...
        log.info("flag {} -> {}", event.flagKey(), event.resolvedValue());
    };
}
```

### Health endpoint

When Actuator is on the classpath the starter registers `OpenFlagsHealthIndicator`. The
indicator reports `UP` for `READY`, `OUT_OF_SERVICE` for `DEGRADED`/`STALE` (`STALE` is deprecated, removal in 2.0) and `DOWN`
otherwise. The response always includes `provider.state`. When the active provider
implements `ProviderDiagnostics`, the response is enriched with `provider.type` plus a
provider-specific map:

- `file` provider — `file.path`, `file.format`, `file.last_reload`, `file.watcher_alive`,
  `file.flag_count`.
- `remote` provider — `remote.base_url`, `remote.poll_interval_ms`, `remote.cache_ttl_ms`,
  `remote.state`, `remote.last_fetch`, `remote.flag_count`,
  `remote.consecutive_failures`, `remote.circuit_open`, `remote.next_poll_in_ms`.
- `hybrid` provider — `hybrid.routing_target` (`remote` or `file`), `hybrid.snapshot_path`,
  `hybrid.snapshot_age_seconds`, `hybrid.last_snapshot_write`, plus all keys exposed by
  the underlying remote and file providers.

### Circuit breaker (remote provider)

The remote provider tracks consecutive poll failures and applies exponential backoff
once the threshold is reached. Defaults: `failure-threshold=5`, `max-backoff=5m`. Both
are exposed as Spring properties and surfaced via the health indicator.

```yaml
openflags:
  remote:
    failure-threshold: 5
    max-backoff: 5m
```

### MDC and PII

When `openflags.audit.mdc-enabled=true` the client sets `openflags.flag_key` and
`openflags.targeting_key` on the SLF4J `MDC` for the duration of each evaluation,
restoring the previous values afterwards (nesting-safe). Remember that
`openflags.targeting_key` may carry PII; keep it disabled by default in environments
where logs are not sufficiently controlled.

---

## Contributing

Contributions are welcome. Please open an issue before submitting a pull request for significant changes.

- Branch off `main`, keep commits focused
- All public code (Javadoc, logs, exceptions) must be in English
- Run `mvn verify` before opening a PR; CI must be green

---

## License

[Apache License 2.0](LICENSE)
