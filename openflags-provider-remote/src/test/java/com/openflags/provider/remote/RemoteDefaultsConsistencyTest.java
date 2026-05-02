package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that {@link RemoteFlagProviderBuilder} initial defaults match the
 * {@code DEFAULT_*} constants in {@link RemoteProviderConfig}, so changing one
 * without the other surfaces immediately.
 */
class RemoteDefaultsConsistencyTest {

        @Test
        void builderDefaults_alignWithConfigConstants() throws Exception {
                RemoteFlagProviderBuilder builder = RemoteFlagProviderBuilder
                                .forUrl(URI.create("http://example.test"));

                assertThat(readField(builder, "connectTimeout"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_CONNECT_TIMEOUT);
                assertThat(readField(builder, "requestTimeout"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_REQUEST_TIMEOUT);
                assertThat(readField(builder, "pollInterval"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_POLL_INTERVAL);
                assertThat(readField(builder, "cacheTtl"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_CACHE_TTL);
                assertThat(readField(builder, "failureThreshold"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD);
                assertThat(readField(builder, "maxBackoff"))
                                .isEqualTo(RemoteProviderConfig.DEFAULT_MAX_BACKOFF);
        }

        @Test
        void blankOptionalFields_resolveToConstantDefaults() {
                // null flagsPath and blank userAgent are normalized by the compact
                // constructor to the DEFAULT_* constants — guards both the
                // fallback logic and that the constants themselves are wired in.
                RemoteProviderConfig cfg = new RemoteProviderConfig(
                                URI.create("http://example.test"),
                                null, null, null,
                                RemoteProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                                RemoteProviderConfig.DEFAULT_REQUEST_TIMEOUT,
                                RemoteProviderConfig.DEFAULT_POLL_INTERVAL,
                                RemoteProviderConfig.DEFAULT_CACHE_TTL,
                                "  ");
                assertThat(cfg.userAgent()).isEqualTo(RemoteProviderConfig.DEFAULT_USER_AGENT);
                assertThat(cfg.flagsPath()).isEqualTo(RemoteProviderConfig.DEFAULT_FLAGS_PATH);
        }

        private static Object readField(Object target, String name) throws Exception {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
        }
}
