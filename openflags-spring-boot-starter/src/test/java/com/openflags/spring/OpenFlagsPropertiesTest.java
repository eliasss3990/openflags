package com.openflags.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = OpenFlagsPropertiesTest.Config.class)
@TestPropertySource(properties = {
        "openflags.provider=file",
        "openflags.file.path=classpath:flags-test.yml",
        "openflags.file.watch-enabled=false"
})
class OpenFlagsPropertiesTest {

    @EnableConfigurationProperties(OpenFlagsProperties.class)
    static class Config {}

    @Autowired
    private OpenFlagsProperties properties;

    @Test
    void bindsProvider() {
        assertThat(properties.getProvider()).isEqualTo("file");
    }

    @Test
    void bindsFilePath() {
        assertThat(properties.getFile().getPath()).isEqualTo("classpath:flags-test.yml");
    }

    @Test
    void bindsWatchEnabled() {
        assertThat(properties.getFile().isWatchEnabled()).isFalse();
    }

    @Test
    void defaults_whenNotConfigured() {
        OpenFlagsProperties defaults = new OpenFlagsProperties();
        assertThat(defaults.getProvider()).isEqualTo("file");
        assertThat(defaults.getFile().getPath()).isEqualTo("classpath:flags.yml");
        assertThat(defaults.getFile().isWatchEnabled()).isTrue();
    }
}
