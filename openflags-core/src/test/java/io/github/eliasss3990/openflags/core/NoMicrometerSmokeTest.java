package io.github.eliasss3990.openflags.core;

import io.github.eliasss3990.openflags.core.model.Flag;
import io.github.eliasss3990.openflags.core.model.FlagType;
import io.github.eliasss3990.openflags.core.model.FlagValue;
import io.github.eliasss3990.openflags.core.provider.FlagProvider;
import io.github.eliasss3990.openflags.core.provider.ProviderState;
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
        public void addChangeListener(io.github.eliasss3990.openflags.core.event.FlagChangeListener l) {
        }

        @Override
        public void removeChangeListener(io.github.eliasss3990.openflags.core.event.FlagChangeListener l) {
        }

        @Override
        public void shutdown() {
        }
    }
}
