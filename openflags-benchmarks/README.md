# openflags-benchmarks

JMH benchmarks for openflags. **Standalone** — not part of the published Maven
reactor and not run by CI. Built against the published `1.0.0` artifacts on
Maven Central.

## Running

```bash
cd openflags-benchmarks
mvn clean package
java -jar target/benchmarks.jar
```

Run a specific benchmark:

```bash
java -jar target/benchmarks.jar EvaluationBenchmark.booleanFlagOn
```

Common JMH flags:

```bash
# More warm-up + measurement iterations
java -jar target/benchmarks.jar -wi 5 -i 10 -f 2

# JSON output for tracking over time
java -jar target/benchmarks.jar -rf json -rff results.json
```

## What is measured

`EvaluationBenchmark` covers the hot path of a typical evaluation against the
`FileFlagProvider`:

| Benchmark                  | Scenario                                              |
|----------------------------|-------------------------------------------------------|
| `booleanFlagOn`            | Boolean flag with value `true` and empty context.     |
| `booleanFlagOff`           | Boolean flag with value `false` and empty context.    |
| `stringFlag`               | String flag, empty context.                           |
| `booleanFlagWithContext`   | Boolean flag with a populated targeting context.      |
| `missingFlagFallback`      | Lookup of a non-existent flag (default-value path).   |

Modes: throughput (ops/μs) and average time per call (μs).

## Caveats

These numbers reflect a single JVM with the file provider only and do not include
network latency, classloader overhead in container starts, or the cost of
emitting metrics to a real registry. Treat them as a regression detector
between releases, not as an absolute SLO.

## When to update

After every release that touches the evaluation hot path
(`OpenFlagsClient`, `FlagProvider` implementations, `RolloutEvaluator`,
metrics recording). Update `<openflags.version>` in `pom.xml` to the version
under test, run, and store results under `docs/perf/` (not yet created — add
when there is something worth comparing).
