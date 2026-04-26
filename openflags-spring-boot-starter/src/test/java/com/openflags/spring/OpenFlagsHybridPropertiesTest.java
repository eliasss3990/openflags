package com.openflags.spring;

import com.openflags.provider.hybrid.SnapshotFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenFlagsHybridPropertiesTest.Config.class)
class OpenFlagsHybridPropertiesTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("openflags.provider", () -> "hybrid");
        registry.add("openflags.hybrid.snapshot-path",
                () -> tempDir.resolve("snap.json").toString());
        registry.add("openflags.hybrid.snapshot-format", () -> "YAML");
        registry.add("openflags.hybrid.watch-snapshot", () -> "false");
        registry.add("openflags.hybrid.snapshot-debounce", () -> "300ms");
        registry.add("openflags.hybrid.fail-if-no-fallback", () -> "true");
    }

    @EnableConfigurationProperties(OpenFlagsProperties.class)
    static class Config {}

    @Autowired
    private OpenFlagsProperties properties;

    @Test
    void bindsSnapshotPath() {
        assertThat(properties.getHybrid().getSnapshotPath())
                .isEqualTo(tempDir.resolve("snap.json").toString());
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
