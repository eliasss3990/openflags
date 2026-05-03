# 8. Java 21 as minimum baseline

- **Status**: Accepted (implemented in PR #54)
- **Date**: 2026-05-03
- **Deciders**: Elias Gonzalez (maintainer)

## Context

Until PR #54, the project enforced a minimum Java version of 17 via the
`maven-enforcer-plugin` `requireJavaVersion` rule (`[17,)`). That choice was
made early in the lifecycle of openflags, when Java 17 was the most recent LTS
release and the safest common denominator for library consumers.

Several factors made it worth revisiting that baseline:

- The project source and tests were already starting to lean on language and
  API features introduced after 17 (pattern matching for `switch`, sealed type
  refinements, `HttpClient` ergonomics, `Thread.ofVirtual`, `SequencedCollection`),
  even when those usages could still be backported. Keeping the baseline at 17
  forced periodic discipline to avoid newer constructs, with little upside.
- Java 21 has been the active LTS since September 2023 and, by the time this
  decision is recorded (2026-05-03), it has been GA for more than two years and
  is widely adopted in production. Java 17 is approaching end of premier
  support for several distributions.
- The Spring ecosystem, which is the most likely host for openflags consumers,
  has been recommending Java 21 since Spring Boot 3.2 and Spring Framework 6.1.
  Spring Boot 3.4 and later release notes explicitly highlight Java 21 as the
  preferred runtime. Aligning the library baseline with the recommended
  consumer runtime simplifies the support matrix.
- Virtual threads (JEP 444), one of the most relevant features for I/O-bound
  workloads such as the remote HTTP poller in `RemoteHttpFlagProvider`, are
  only available from 21 onwards. Building against 21 lets consumers wire the
  provider on a virtual-thread executor without relying on preview flags or
  third-party backports.
- The project has not yet published a stable 1.x release that promised long
  term support of Java 17. The window to adjust the baseline without breaking
  an explicit compatibility contract is now; pushing the change later would be
  significantly more disruptive.
- Maintenance cost: keeping CI matrices, toolchains and Javadoc generation
  aligned across two LTS versions has a non-trivial cost for a single-maintainer
  project. Reducing the supported set to one LTS is a deliberate scope cut.

## Decision

Raise the minimum supported Java version from 17 to 21. Concretely:

- Update the `requireJavaVersion` enforcer rule in the root `pom.xml` from
  `[17,)` to `[21,)`.
- Keep `maven.compiler.release` (or the equivalent `--release` flag) at `21`
  so that the compiled bytecode targets exactly the documented baseline and
  prevents accidental usage of preview or post-21 APIs.
- Treat Java 21 as the floor: the project may be tested against newer LTS
  releases as they appear, but there is no commitment to keep working below
  21.
- Document the new baseline in `docs/getting-started.md` and the README so
  that consumers discover the requirement before adding the dependency.

This decision is recorded retroactively: the change has already shipped in
PR #54.

## Consequences

### Positive

- **Active LTS alignment**: 21 is the current LTS and benefits from the
  longest remaining support window of any stable release at the time of this
  ADR.
- **Modern language features available internally**: pattern matching for
  `switch`, record patterns, sealed type exhaustiveness checks and the
  `SequencedCollection` interface can be used in production code and tests
  without guards or shims.
- **Virtual threads for consumers**: applications wiring openflags providers
  on `Executors.newVirtualThreadPerTaskExecutor()` benefit from cheap blocking
  I/O without configuration tricks. The remote HTTP provider in particular
  fits naturally on a virtual-thread carrier.
- **Smaller test matrix**: CI only needs to validate one LTS, which reduces
  build minutes and removes a class of "works on 21, broken on 17" regressions.
- **Simpler documentation**: a single supported Java version removes
  conditional sections in getting-started and reduces the surface area for
  user confusion.
- **Toolchain freedom**: maintainers can adopt newer Maven plugins, Surefire
  releases and static analysis tools that have already moved their own floor
  to 21 without negotiating compatibility.

### Negative / Risks

- **Breaks consumers stuck on Java 17**: any project that cannot upgrade its
  runtime will be unable to consume future openflags releases. This is the
  main cost of the decision.
- **Perceived aggressiveness**: some library ecosystems still treat 17 as the
  default baseline. Adopting 21 may appear premature to users who compare
  baselines across libraries.
- **No backport branch**: there is no maintained `1.x-java17` branch. Bug
  fixes will not be ported back to a 17-compatible line.

#### Mitigations

- The requirement is enforced at build time by the `requireJavaVersion`
  enforcer rule, so consumers fail fast with a clear message instead of
  encountering obscure `UnsupportedClassVersionError` at runtime.
- The baseline is documented in `docs/getting-started.md` under the
  prerequisites section and called out in the README so that new users see it
  before integrating the library.
- Older openflags versions remain available in Maven Central for consumers
  that cannot migrate; they will simply not receive new features.

### Migration

For consumers currently on Java 17:

1. Upgrade the project JDK to a 21 distribution (Temurin, Liberica, Corretto,
   Zulu, etc.). No specific vendor is required; any 21+ runtime is acceptable.
2. Update `maven.compiler.release` (or Gradle equivalent) to `21`.
3. Re-run the build. The enforcer rule will surface any remaining 17-only
   toolchain, and the compiler will reject post-17 syntax that is not yet
   supported by the consumer build.
4. No source-level changes are required in code that uses openflags: the
   public API is unchanged by this ADR. The migration is purely a runtime
   upgrade.

There is no compatibility shim and no plan to provide one. Multi-release JAR
support was considered (see Alternatives) and rejected.

## Alternatives considered

### Keep Java 17 as the baseline

Rejected. Holding 17 indefinitely would slow down internal modernization,
prevent the use of virtual threads in examples and integration tests, and
delay the eventual jump to a newer LTS without removing it. The cost of
delaying the decision grows with the number of released versions that promise
17 compatibility.

### Dual support: build for 17, test on 17 and 21

Rejected. Maintaining two CI matrices, two toolchains and a
"do-not-use-after-17" linter for a one-maintainer project was judged too
expensive for the benefit. Most consumers in the openflags target audience
(Spring Boot 3.x backends) are already on 21 or planning the move.

### Multi-release JAR (MRJAR)

Rejected. An MRJAR would let the artifact ship 17- and 21-specific class
files in the same JAR. It adds significant build complexity (separate
source roots, careful API parity, special Surefire configuration) and the
project does not have a feature today that genuinely needs version-specific
implementations. Revisiting MRJARs may make sense if a future feature
requires API differences across versions, but it is not justified solely to
keep 17 alive.

### Adopt preview features on 21

Rejected. Preview features (`--enable-preview`) are explicitly out of scope
for a library: forcing consumers to enable preview flags propagates an
unstable contract to every dependent project. The baseline is exactly Java
21 GA features, with no preview flags required.

### Jump directly to Java 25 (next LTS)

Rejected at the time of this decision. Java 25 was not yet GA when PR #54
was prepared, and even after its release the library should not require the
newest LTS in its first weeks of availability. The natural next step is to
re-evaluate the baseline once 25 has reached broad adoption, and at that
point this ADR should be superseded by a new one rather than amended.

## References

- PR #54 — bump enforcer rule to `[21,)` and align `maven.compiler.release`.
- `pom.xml` — `maven-enforcer-plugin` `requireJavaVersion` rule and
  `maven.compiler.release` property.
- `docs/getting-started.md` — prerequisites section documenting the Java 21
  requirement for consumers.
- ADR-2 (`002-flag-provider-lifecycle.md`) — lifecycle decisions that assume
  modern language features available from 21 onwards.
- JEP 444 — Virtual Threads (final in Java 21).
- Spring Boot release notes recommending Java 21 as the preferred runtime
  from 3.2 onwards.
