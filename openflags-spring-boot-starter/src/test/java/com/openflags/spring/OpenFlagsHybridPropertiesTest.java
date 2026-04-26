package com.openflags.spring;

import com.openflags.provider.hybrid.SnapshotFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenFlagsHybridPropertiesTest.Config.class)
@TestPropertySource(properties = {
        "openflags.provider=hybrid",
        "openflags.hybrid.snapshot-path=/tmp/snap.json",
        "openflags.hybrid.snapshot-format=YAML",
        "openflags.hybrid.watch-snapshot=false",
        "openflags.hybrid.snapshot-debounce=300ms",
        "openflags.hybrid.fail-if-no-fallback=true"
})
class OpenFlagsHybridPropertiesTest {

    @EnableConfigurationProperties(OpenFlagsProperties.class)
    static class Config {}

    @Autowired
    private OpenFlagsProperties properties;

    @Test
    void bindsSnapshotPath() {
        assertThat(properties.getHybrid().getSnapshotPath()).isEqualTo("/tmp/snap.json");
    }

    @Test
    void bindsSnapshotFormat() {
        assertThat(properties.getHybrid().getSnapshotFormat()).isEqualTo(SnapshotFormat.YAML);
    }

    @Test
    void bindsWatchSnapshot() {
        assertThat(properties.getHybrid().isWatchSnapshot()).isFalse();
    }

    @Test
    void bindsSnapshotDebounce() {
        assertThat(properties.getHybrid().getSnapshotDebounce()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void bindsFailIfNoFallback() {
        assertThat(properties.getHybrid().isFailIfNoFallback()).isTrue();
    }

    @Test
    void defaults_whenNotConfigured() {
        OpenFlagsProperties.HybridProperties defaults = new OpenFlagsProperties.HybridProperties();
        assertThat(defaults.getSnapshotPath()).isNull();
        assertThat(defaults.getSnapshotFormat()).isEqualTo(SnapshotFormat.JSON);
        assertThat(defaults.isWatchSnapshot()).isTrue();
        assertThat(defaults.getSnapshotDebounce()).isEqualTo(Duration.ofMillis(500));
        assertThat(defaults.isFailIfNoFallback()).isFalse();
    }
}
