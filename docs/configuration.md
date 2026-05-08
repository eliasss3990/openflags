# Configuration Reference

## Spring Boot properties

All properties are under the `openflags` prefix.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.provider` | `String` | `file` | Provider type. Accepted values (case-sensitive): `file`, `remote`, `hybrid`. |

### Example

```yaml
openflags:
  provider: file
```

---

## File provider properties

Applied when `openflags.provider=file`.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.file.path` | `String` | `classpath:flags.yml` | Path to the flag definition file. Supports `classpath:` and `file:` prefixes. |
| `openflags.file.watch-enabled` | `Boolean` | `true` | Enable hot reload when the flag file changes. Automatically disabled for files inside JARs. |
| `openflags.file.debounce` | `Duration` | `200ms` | Debounce window applied to filesystem change events. Must be strictly positive. Has no effect when `watch-enabled=false`. |

### Example

```yaml
openflags:
  provider: file
  file:
    path: file:/etc/myapp/flags.yml
    watch-enabled: true
    debounce: 200ms
```

---

## Remote provider properties

Applied when `openflags.provider=remote`. `openflags.remote.base-url` is required.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.remote.base-url` | `URI` | _(required)_ | Base URL of the remote flags backend. |
| `openflags.remote.flags-path` | `String` | `/flags` | Path appended to the base URL for fetching flags. |
| `openflags.remote.poll-interval` | `Duration` | `30s` | Polling interval. Minimum: 5 seconds. |
| `openflags.remote.cache-ttl` | `Duration` | `5m` | Cache TTL. Must be greater than or equal to `poll-interval`. |
| `openflags.remote.connect-timeout` | `Duration` | `5s` | HTTP connect timeout. |
| `openflags.remote.request-timeout` | `Duration` | `10s` | HTTP request timeout. Must not exceed `poll-interval`. |
| `openflags.remote.user-agent` | `String` | `openflags-java` | `User-Agent` header value. Defaults to `openflags-java` when null or blank. |
| `openflags.remote.auth-header-name` | `String` | _(none)_ | HTTP header name for authentication, e.g. `X-API-Key`. May be omitted. |
| `openflags.remote.auth-header-secret` | `String` | _(none)_ | HTTP header value for authentication. The `-secret` suffix triggers Spring Boot Actuator's automatic value sanitization on `/actuator/configprops` and `/actuator/env`. |
| `openflags.remote.failure-threshold` | `Integer` | `5` | Consecutive poll failures before exponential backoff kicks in. Must be between 1 and 100. |
| `openflags.remote.max-backoff` | `Duration` | `5m` | Upper bound for the backoff delay applied when the circuit is open. Must be >= `poll-interval`. |

### Example

```yaml
openflags:
  provider: remote
  remote:
    base-url: https://flags.example.com
    auth-header-name: X-API-Key
    auth-header-secret: ${FLAGS_API_KEY}
    poll-interval: 30s
    cache-ttl: 5m
```

---

## Hybrid provider properties

Applied when `openflags.provider=hybrid`. Inherits all `openflags.remote.*` properties for the remote sub-provider. `openflags.remote.base-url` and `openflags.hybrid.snapshot-path` are both required.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.hybrid.snapshot-path` | `String` | _(required)_ | Filesystem path of the local snapshot file. Used as fallback when the remote is unavailable. |
| `openflags.hybrid.snapshot-format` | `SnapshotFormat` | `JSON` | Snapshot file format. Accepted values: `JSON`, `YAML`. |
| `openflags.hybrid.watch-snapshot` | `Boolean` | `true` | Enable filesystem watching of the snapshot for manual edits. |
| `openflags.hybrid.snapshot-debounce` | `Duration` | `500ms` | Debounce window for ignoring self-induced file events after the provider writes a new snapshot. |
| `openflags.hybrid.fail-if-no-fallback` | `Boolean` | `false` | If `true`, fail initialization when neither the remote nor the snapshot can produce data. |

### Example

```yaml
openflags:
  provider: hybrid
  remote:
    base-url: https://flags.example.com
    poll-interval: 60s
  hybrid:
    snapshot-path: /var/cache/myapp/flags-snapshot.json
    snapshot-format: JSON
    fail-if-no-fallback: false
```

---

## Audit properties

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.audit.mdc-enabled` | `Boolean` | `false` | If `true`, sets `openflags.flag_key` and `openflags.targeting_key` in SLF4J MDC during every evaluation. **Warning:** the targeting key may contain PII; review your logging pipeline before enabling. |

### Example

```yaml
openflags:
  audit:
    mdc-enabled: true
```

---

## Metrics properties

Metrics are activated only when `micrometer-core` is on the classpath and a `MeterRegistry` bean is available.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.metrics.enabled` | `Boolean` | `true` | Enable Micrometer metrics. Even when `true`, no metrics are emitted if `micrometer-core` is absent or no `MeterRegistry` bean is exposed. |
| `openflags.metrics.tag-flag-key` | `Boolean` | `true` | If `true`, adds a `flag` tag to per-flag counters and timers. Disable to reduce cardinality when the number of flags exceeds ~200. |
| `openflags.metrics.tags` | `Map<String, String>` | _(empty)_ | Static tags applied to all openflags metrics. Useful for environment, region, or service identification. |

### Example

```yaml
openflags:
  metrics:
    enabled: true
    tag-flag-key: true
    tags:
      env: production
      region: us-east-1
```

---

## Health states mapping

When `spring-boot-starter-actuator` is on the classpath, the `/actuator/health` endpoint includes an `openflags` component:

```json
{
  "components": {
    "openflags": {
      "status": "UP",
      "details": {
        "provider.state": "READY"
      }
    }
  }
}
```

| Provider state | Health status | Notes |
|---|---|---|
| `READY` | `UP` | |
| `NOT_READY`, `ERROR` | `DOWN` | |
| `DEGRADED` | `OUT_OF_SERVICE` | |
| `SHUTDOWN` | `DOWN` | |
| `STALE` | `OUT_OF_SERVICE` | _Deprecated, scheduled for removal in 2.0_ |

---

## Flag file format

### YAML

```yaml
flags:
  <key>:
    type: boolean | string | number | object
    value: <value>
    enabled: true          # optional — defaults to true
    description: "..."     # optional metadata
```

### JSON

```json
{
  "flags": {
    "dark-mode": {
      "type": "boolean",
      "value": true
    }
  }
}
```

Supported extensions: `.yml`, `.yaml`, `.json`. Any other extension throws a `ProviderException` at startup.

### Type mapping

| Flag type | Java type returned | Accessor |
|---|---|---|
| `boolean` | `boolean` / `Boolean` | `getBooleanValue()` |
| `string` | `String` | `getStringValue()` |
| `number` | `double` / `Double` | `getNumberValue()` |
| `object` | `Map<String, Object>` | `getObjectValue()` |

Object values are stored as immutable maps. Nested maps and lists are supported but deep immutability is not enforced.

### Disabled flags

A flag with `enabled: false` always returns the caller's `defaultValue` with reason `FLAG_DISABLED`, regardless of its stored value.

### Empty flag list

An explicit empty flags object is valid:

```yaml
flags: {}
```

A file with no `flags` key, or with `flags:` and no value, throws `ProviderException` at parse time.

---

## Evaluation context

`EvaluationContext` is accepted by all evaluation methods and used for targeting rule evaluation (user segmentation, percentage rollout).

```java
EvaluationContext ctx = EvaluationContext.of("user-123");
boolean result = client.getBooleanValue("my-flag", false, ctx);
```
