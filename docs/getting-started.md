# Getting Started

This guide walks through adding openflags to a new project, defining your first flags, and evaluating them at runtime.

## Prerequisites

- Java 21 or later
- Maven 3.9+

---

## Option A — Spring Boot application

### 1. Add the starter

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Create `src/main/resources/flags.yml`

```yaml
flags:
  new-checkout-flow:
    type: boolean
    value: false
    description: "Enables the redesigned checkout UI"

  api-timeout-ms:
    type: number
    value: 3000
```

### 3. Inject `OpenFlagsClient`

```java
@RestController
public class CheckoutController {

    private final OpenFlagsClient flags;

    public CheckoutController(OpenFlagsClient flags) {
        this.flags = flags;
    }

    @GetMapping("/checkout")
    public String checkout() {
        if (flags.getBooleanValue("new-checkout-flow", false)) {
            return "new-checkout";
        }
        return "classic-checkout";
    }
}
```

That's it. The `OpenFlagsClient` bean is created automatically and the flag file is loaded on startup.

---

## Option B — Standalone Java (no Spring Boot)

### 1. Add the provider dependency

```xml
<dependency>
    <groupId>io.github.eliasss3990</groupId>
    <artifactId>openflags-provider-file</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Build the client

```java
FileFlagProvider provider = FileFlagProvider.builder()
        .path(Path.of("flags.yml"))
        .watchEnabled(true)
        .build();

OpenFlagsClient client = OpenFlagsClient.builder()
        .provider(provider)
        .build();
```

`OpenFlagsClientBuilder.build()` calls `provider.init()` automatically.

### 3. Evaluate and shut down

```java
boolean enabled = client.getBooleanValue("new-checkout-flow", false);

// When the application exits:
client.shutdown();
```

---

## Hot reload

When `watch-enabled` is `true` (the default), the provider watches the flag file for changes using `java.nio.file.WatchService`. When the file is saved:

1. A 200 ms debounce window collapses rapid consecutive saves into one reload.
2. The provider re-parses the file and atomically swaps the flag map.
3. Registered `FlagChangeListener`s receive events for each added, updated, or removed flag.
4. If the file is mid-write and unparseable, one retry is attempted after another 200 ms. If the retry also fails, the previous flags are retained and the provider state is set to `ERROR`.

File watching is automatically disabled for files inside JARs (e.g. `classpath:` resources packaged in a Spring Boot fat-JAR). An INFO message is logged in that case.

---

## Listening for changes

```java
client.addChangeListener(event -> {
    System.out.printf("Flag '%s' %s%n", event.flagKey(), event.changeType());
});
```

`FlagChangeEvent` carries the flag key, type, old value, new value, and change type (`CREATED`, `UPDATED`, `DELETED`).

Remove a listener when it is no longer needed:

```java
FlagChangeListener listener = event -> { /* ... */ };
client.addChangeListener(listener);
// later:
client.removeChangeListener(listener);
```

> **Note:** always keep a reference to the listener instance. Method references (e.g. `this::onFlagChange`) create a new object on each expression and cannot be removed by value.
