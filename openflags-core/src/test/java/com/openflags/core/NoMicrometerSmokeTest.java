package com.openflags.core;

import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke test that exercises {@link OpenFlagsClient} on a classpath that
 * has Micrometer excluded. Validates ADR-501: the SDK must work fully
 * (build, evaluate, shutdown) when {@code io.micrometer.*} is not present.
 *
 * <p>
 * Tagged {@code no-micrometer} so it only runs under the Maven profile
 * {@code -P no-micrometer}, which excludes the Micrometer artifacts from
 * the test classpath. The default Surefire run excludes this group.
 */
@Tag("no-micrometer")
class NoMicrometerSmokeTest {

    @Test
    void micrometerAbsentFromClasspath() {
        assertThatThrownBy(() -> Class.forName(
                "io.micrometer.core.instrument.MeterRegistry",
                false,
                getClass().getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void clientBuildsAndEvaluatesWithoutMicrometer() {
        StubProvider provider = new StubProvider();
        OpenFlagsClient client = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("stub")
                .build();
        try {
            for (int i = 0; i < 1000; i++) {
                boolean v = client.getBooleanValue("flag-on", false);
                assertThat(v).isTrue();
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    void metricsRegistryRejectsWhenMicrometerMissing() {
        OpenFlagsClientBuilder b = OpenFlagsClient.builder()
                .provider(new StubProvider());
        // Use a stub whose classloader is the same loader running this test:
        // Micrometer is excluded from this classpath, so the builder's
        // reflective lookup must fail and surface IllegalStateException.
        Object fakeRegistry = new FakeRegistry();
        assertThatThrownBy(() -> b.metricsRegistry(fakeRegistry))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class FakeRegistry { }

    private static final class StubProvider implements FlagProvider {
        private final Flag flag = new Flag(
                "flag-on", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true,
                Collections.emptyMap());

        @Override
        public void init() {
        }

        @Override
        public Optional<Flag> getFlag(String key) {
            return "flag-on".equals(key) ? Optional.of(flag) : Optional.empty();
        }

        @Override
        public Map<String, Flag> getAllFlags() {
            return Map.of("flag-on", flag);
        }

        @Override
        public ProviderState getState() {
            return ProviderState.READY;
        }

        @Override
        public void addChangeListener(com.openflags.core.event.FlagChangeListener l) {
        }

        @Override
        public void removeChangeListener(com.openflags.core.event.FlagChangeListener l) {
        }

        @Override
        public void shutdown() {
        }
    }
}
