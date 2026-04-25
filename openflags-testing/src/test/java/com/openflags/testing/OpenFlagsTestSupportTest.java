package com.openflags.testing;

import com.openflags.core.OpenFlagsClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OpenFlagsTestSupportTest {

    @Test
    void withFlag_boolean() {
        OpenFlagsClient client = OpenFlagsTestSupport.withFlag("feature", true);
        assertThat(client.getBooleanValue("feature", false)).isTrue();
        client.shutdown();
    }

    @Test
    void withFlag_string() {
        OpenFlagsClient client = OpenFlagsTestSupport.withFlag("theme", "dark");
        assertThat(client.getStringValue("theme", "light")).isEqualTo("dark");
        client.shutdown();
    }

    @Test
    void withFlag_number() {
        OpenFlagsClient client = OpenFlagsTestSupport.withFlag("rate", 0.5);
        assertThat(client.getNumberValue("rate", 0.0)).isEqualTo(0.5);
        client.shutdown();
    }

    @Test
    void createTestClient_withSetup() {
        OpenFlagsClient client = OpenFlagsTestSupport.createTestClient(p -> {
            p.setBoolean("a", true);
            p.setString("b", "hello");
        });
        assertThat(client.getBooleanValue("a", false)).isTrue();
        assertThat(client.getStringValue("b", "")).isEqualTo("hello");
        client.shutdown();
    }
}
