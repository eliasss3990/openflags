package com.openflags.spring;

import com.openflags.provider.remote.RemoteProviderConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard: literal defaults in {@link OpenFlagsProperties.RemoteProperties}
 * must mirror the {@code DEFAULT_*} constants in {@link RemoteProviderConfig}.
 * Literals are preserved (instead of referencing the constants) because
 * {@code openflags-provider-remote} is an optional dependency of the starter
 * and the always-loaded {@code OpenFlagsProperties} class must not require it.
 */
class RemoteDefaultsAlignmentTest {

        @Test
        void remotePropertiesDefaults_matchRemoteProviderConfigConstants() {
                OpenFlagsProperties.RemoteProperties props = new OpenFlagsProperties.RemoteProperties();

                assertThat(props.getFlagsPath())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_FLAGS_PATH);
                assertThat(props.getConnectTimeout())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_CONNECT_TIMEOUT);
                assertThat(props.getRequestTimeout())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_REQUEST_TIMEOUT);
                assertThat(props.getPollInterval())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_POLL_INTERVAL);
                assertThat(props.getCacheTtl())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_CACHE_TTL);
                assertThat(props.getFailureThreshold())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD);
                assertThat(props.getMaxBackoff())
                                .isEqualTo(RemoteProviderConfig.DEFAULT_MAX_BACKOFF);
        }
}
