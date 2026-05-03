# openflags Spring Boot quickstart

Minimal Spring Boot 3 app showing how to consume openflags via the starter.

- File-based provider with hot reload
- Targeting rule by `country` attribute
- MDC keys propagated into log lines
- Micrometer Prometheus endpoint at `/actuator/prometheus`

## Run

```bash
mvn -pl examples/spring-boot-quickstart spring-boot:run
# or
cd examples/spring-boot-quickstart
mvn spring-boot:run
```

Then:

```bash
# Argentine user → "Bienvenido" + new checkout
curl 'http://localhost:8080/checkout?user=u-1&country=AR'

# US user → falls through to defaults
curl 'http://localhost:8080/checkout?user=u-2&country=US'

# Metrics
curl http://localhost:8080/actuator/prometheus | grep openflags
```

Edit `src/main/resources/flags.yml` while the app is running and the change
is picked up without a restart.

## What to look at

- [`QuickstartApplication.java`](src/main/java/com/openflags/example/quickstart/QuickstartApplication.java)
  — single `OpenFlagsClient` is autowired by the starter; pass an
  `EvaluationContext` per request.
- [`application.yml`](src/main/resources/application.yml) — only the
  `openflags.*` section is library-specific. The logging pattern shows MDC
  keys.
- [`flags.yml`](src/main/resources/flags.yml) — flag definitions with a
  targeting rule.

For a full property reference, see the main [README](../../README.md) and
[docs/observability.md](../../docs/observability.md).
