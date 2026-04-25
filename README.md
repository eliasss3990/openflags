# openflags

[![CI](https://github.com/eliasss3990/openflags/actions/workflows/ci.yml/badge.svg)](https://github.com/eliasss3990/openflags/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Lightweight, SDK-first feature flag library for Java and Spring Boot.

Evaluate boolean, string, number, and object flags from a local YAML or JSON file — no external service required. Hot reload detects file changes at runtime without restarting the application. The provider model is extensible: remote and hybrid providers are planned for future phases.

---

## Features

- **Type-safe evaluation** — `getBooleanValue`, `getStringValue`, `getNumberValue`, `getObjectValue`
- **File-based provider** — YAML and JSON, configurable path
- **Hot reload** — automatic reload on file change via `WatchService`, with debounce and mid-write retry
- **Spring Boot auto-configuration** — zero-config setup via `openflags-spring-boot-starter`
- **Spring Actuator integration** — `/actuator/health` reports provider state when Actuator is on the classpath
- **In-memory provider** — for testing without files
- **Extensible** — implement `FlagProvider` to plug in any backend

---

## Requirements

- Java 17 or later
- Maven 3.8+
- Spring Boot 3.x (for the starter; core is framework-agnostic)

---

## Quick start

### Standalone (no Spring Boot)

**1. Add the dependency**

```xml
<dependency>
    <groupId>com.openflags</groupId>
    <artifactId>openflags-provider-file</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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
    <groupId>com.openflags</groupId>
    <artifactId>openflags-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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
  provider: file                        # currently the only supported provider
  file:
    path: classpath:flags.yml           # supports classpath: and file: prefixes
    watch-enabled: true                 # hot reload (auto-disabled for files inside JARs)
```

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
    <groupId>com.openflags</groupId>
    <artifactId>openflags-testing</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

---

## Modules

| Module | Description |
|---|---|
| `openflags-core` | Core SDK: `OpenFlagsClient`, `FlagProvider` interface, evaluation engine |
| `openflags-provider-file` | File-based provider with YAML/JSON parsing and hot reload |
| `openflags-spring-boot-starter` | Spring Boot auto-configuration and Actuator health indicator |
| `openflags-testing` | `InMemoryFlagProvider` for unit and integration tests |
| `openflags-bom` | Bill of Materials for consistent dependency management |

---

## Building from source

```bash
git clone https://github.com/eliasss3990/openflags.git
cd openflags
mvn clean verify
```

Requires Java 17+ and Maven 3.8+.

---

## Roadmap

- **Phase 2** — Targeting rules: percentage rollout, user/group segmentation, consistent hashing
- **Phase 3** — Remote provider: OpenAPI contract + `RemoteProvider` implementation
- **Phase 4** — `HybridProvider`: local fallback + remote sync

---

## Contributing

Contributions are welcome. Please open an issue before submitting a pull request for significant changes.

- Branch off `main`, keep commits focused
- All public code (Javadoc, logs, exceptions) must be in English
- Run `mvn verify` before opening a PR; CI must be green

---

## License

[Apache License 2.0](LICENSE)
