# Configuration Reference

## Spring Boot properties

All properties are under the `openflags` prefix.

| Property | Type | Default | Description |
|---|---|---|---|
| `openflags.provider` | `String` | `file` | Provider type. Currently only `file` is supported. |
| `openflags.file.path` | `String` | `classpath:flags.yml` | Path to the flag file. Supports `classpath:` and `file:` prefixes. |
| `openflags.file.watch-enabled` | `Boolean` | `true` | Enable hot reload. Automatically disabled for files inside JARs. |

### Example

```yaml
openflags:
  provider: file
  file:
    path: file:/etc/myapp/flags.yml   # absolute filesystem path
    watch-enabled: true
```

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

Object values are stored as immutable maps. Nested maps and lists are supported but deep immutability is not enforced (Phase 1 limitation).

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

`EvaluationContext` is accepted by all evaluation methods but is not used for value resolution in Phase 1. It is reserved for targeting rules (percentage rollout, user segmentation) planned for Phase 2.

```java
EvaluationContext ctx = EvaluationContext.of("user-123");
boolean result = client.getBooleanValue("my-flag", false, ctx);
// ctx is ignored in Phase 1; result is the same as calling getBooleanValue("my-flag", false)
```

---

## Spring Actuator health

When `spring-boot-starter-actuator` is on the classpath, the `/actuator/health` endpoint includes an `openflags` component:

```json
{
  "components": {
    "openflags": {
      "status": "UP",
      "details": {
        "providerState": "READY"
      }
    }
  }
}
```

| Provider state | Health status |
|---|---|
| `READY` | `UP` |
| `NOT_READY`, `ERROR` | `DOWN` |
| `STALE` | `OUT_OF_SERVICE` |
