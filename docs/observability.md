# Observability

openflags exposes evaluations, polls, snapshot writes and hybrid fallbacks as
**Micrometer** meters, and tags every evaluation with **MDC** keys so log
records carry the flag key being resolved.

This guide is a reference for SREs and dashboard authors. Names and tag keys
listed here are part of the public contract since 1.0 — see
`OpenFlagsMetrics` and `OpenFlagsMdc` for the constants.

## Enabling

The `openflags-spring-boot-starter` wires Micrometer automatically when a
`MeterRegistry` is on the classpath. To disable:

```yaml
openflags:
  metrics:
    enabled: false
  audit:
    mdc-enabled: false
```

In a non-Spring app, build the client with
`OpenFlagsClient.builder().metricsRecorder(new MicrometerMetricsRecorder(registry))`.

## Metrics catalog

### Evaluations

| Meter                                       | Type    | Tags                                                  | Notes                                                      |
|---------------------------------------------|---------|-------------------------------------------------------|------------------------------------------------------------|
| `openflags.evaluations.total`               | counter | `provider.type`, `type`, `reason`, `flag`?, `variant`? | One increment per evaluation.                              |
| `openflags.evaluation.duration`             | timer   | same as above                                          | End-to-end latency including provider lookup.              |
| `openflags.evaluations.errors.total`        | counter | `provider.type`, `type`, `error.type`, `flag`?         | Increment when evaluation falls back to a default value.   |
| `openflags.evaluations.listener.errors.total` | counter | `listener`                                            | Exceptions thrown by user `EvaluationListener` instances.  |

`flag` and `variant` are only attached when flag-key tagging is enabled
(`openflags.metrics.flag-tagged: true`). Disable in environments with
high-cardinality flag keys to avoid metric explosion.

### Remote provider polls

| Meter                          | Type    | Tags                       |
|--------------------------------|---------|----------------------------|
| `openflags.poll.total`         | counter | `provider.type`, `outcome` |
| `openflags.poll.duration`      | timer   | `provider.type`, `outcome` |

`outcome` is `success` or `failure`.

### Hybrid provider

| Meter                              | Type    | Tags                       |
|------------------------------------|---------|----------------------------|
| `openflags.snapshot.writes.total`  | counter | `outcome`                  |
| `openflags.snapshot.write.duration`| timer   | `outcome`                  |
| `openflags.flag_changes.total`     | counter | `change_type`              |
| `openflags.hybrid.fallback.total`  | counter | `from`, `to`               |

`change_type` ∈ {`added`, `removed`, `value_changed`}.
`from`/`to` are the provider names involved in a fallback transition.

## MDC keys

Set automatically around every evaluation when MDC is enabled.

| Key                         | Value                                              |
|-----------------------------|----------------------------------------------------|
| `openflags.flag_key`        | The flag key being evaluated                        |
| `openflags.targeting_key`   | The evaluation context's targeting key, if present  |

Reference them in Logback layouts:

```xml
<pattern>%d %-5level [%X{openflags.flag_key:-}] %logger - %msg%n</pattern>
```

Or in a JSON appender, include them in the structured fields list.

## Sample dashboard panels

Provided as PromQL pseudo-queries; adapt to your exporter.

- **Evaluation throughput per provider**

  ```
  sum by (provider_type) (rate(openflags_evaluations_total[5m]))
  ```

- **Error ratio**

  ```
  sum(rate(openflags_evaluations_errors_total[5m]))
    / sum(rate(openflags_evaluations_total[5m]))
  ```

- **p99 evaluation latency**

  ```
  histogram_quantile(0.99,
    sum by (le, provider_type) (rate(openflags_evaluation_duration_seconds_bucket[5m])))
  ```

- **Remote poll failure rate**

  ```
  sum(rate(openflags_poll_total{outcome="failure"}[5m]))
  ```

- **Hybrid fallback events** — alert if non-zero in a 5-minute window.

## Cardinality guidance

- **Always safe**: `provider.type`, `type`, `reason`, `outcome`, `change_type`,
  `from`, `to`, `error.type`.
- **Bounded but watchable**: `variant` — only as many values as variants you
  define in your flag config.
- **Potentially unbounded**: `flag`, `listener`. Disable `flag-tagged` if your
  flag namespace grows organically; `listener` is bounded to the number of
  registered listener classes.

## Validating the wiring

A simple smoke test:

```java
@Test
void evaluationsAreCounted(@Autowired SimpleMeterRegistry registry,
                           @Autowired OpenFlagsClient client) {
    client.getBoolean("my.flag", false);
    assertThat(registry.find("openflags.evaluations.total").counter().count())
        .isEqualTo(1.0);
}
```

There is also `openflags-testing` for higher-level harnesses.
