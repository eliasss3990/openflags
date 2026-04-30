package com.openflags.testing;

import com.openflags.core.OpenFlagsClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Helper class for setting up openflags in tests.
 * <p>
 * Creates a pre-configured {@link OpenFlagsClient} backed by an {@link InMemoryFlagProvider}.
 * </p>
 *
 * <pre>
 * OpenFlagsClient client = OpenFlagsTestSupport.withFlag("dark-mode", true);
 * assertThat(client.getBooleanValue("dark-mode", false)).isTrue();
 * client.shutdown();
 * </pre>
 */
public final class OpenFlagsTestSupport {

    private OpenFlagsTestSupport() {}

    /**
     * Creates a test client configured by the given setup consumer.
     * <p>
     * The provider passed to {@code setup} is freshly initialized; callers may invoke
     * any combination of {@code setBoolean}/{@code setString}/{@code setNumber}/
     * {@code setObject}/{@code setFlag} to register flags before the client is built.
     * The returned client owns the provider — call {@link OpenFlagsClient#shutdown()}
     * to release resources at the end of the test.
     * </p>
     *
     * @param setup consumer that receives an {@link InMemoryFlagProvider} to configure;
     *              must not be null
     * @return a ready-to-use client backed by the configured provider
     */
    public static OpenFlagsClient createTestClient(Consumer<InMemoryFlagProvider> setup) {
        InMemoryFlagProvider provider = new InMemoryFlagProvider();
        setup.accept(provider);
        return OpenFlagsClient.builder().provider(provider).build();
    }

    /**
     * Creates a test client with a single boolean flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return a ready-to-use client
     */
    public static OpenFlagsClient withFlag(String key, boolean value) {
        return createTestClient(p -> p.setBoolean(key, value));
    }

    /**
     * Creates a test client with a single string flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return a ready-to-use client
     */
    public static OpenFlagsClient withFlag(String key, String value) {
        return createTestClient(p -> p.setString(key, value));
    }

    /**
     * Creates a test client with a single number flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return a ready-to-use client
     */
    public static OpenFlagsClient withFlag(String key, double value) {
        return createTestClient(p -> p.setNumber(key, value));
    }

    /**
     * Creates a test client with a single object flag.
     *
     * @param key   the flag key
     * @param value the flag value as a map
     * @return a ready-to-use client
     */
    public static OpenFlagsClient withObjectFlag(String key, Map<String, Object> value) {
        return createTestClient(p -> p.setObject(key, value));
    }
}
