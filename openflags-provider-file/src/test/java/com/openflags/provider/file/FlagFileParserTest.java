package com.openflags.provider.file;

import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FlagFileParserTest {

    private FlagFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlagFileParser();
    }

    @Test
    void parse_validYaml_returnsAllFlags() {
        Path file = resourcePath("flags-valid.yml");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags).containsKeys("dark-mode", "welcome-message", "rollout-rate", "feature-config", "disabled-flag");
        assertThat(flags.get("dark-mode").type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flags.get("dark-mode").value().asBoolean()).isTrue();
        assertThat(flags.get("dark-mode").enabled()).isTrue();
        assertThat(flags.get("disabled-flag").enabled()).isFalse();
    }

    @Test
    void parse_validYaml_includesMetadata() {
        Path file = resourcePath("flags-valid.yml");
        Flag darkMode = parser.parse(file).get("dark-mode");
        assertThat(darkMode.metadata()).containsEntry("description", "Enables dark mode UI");
    }

    @Test
    void parse_validJson_returnsAllFlags() {
        Path file = resourcePath("flags-valid.json");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags).containsKeys("dark-mode", "theme", "rate");
        assertThat(flags.get("theme").type()).isEqualTo(FlagType.STRING);
        assertThat(flags.get("theme").value().asString()).isEqualTo("dark");
    }

    @Test
    void parse_emptyYaml_returnsEmptyMap() {
        Path file = resourcePath("flags-empty.yml");
        Map<String, Flag> flags = parser.parse(file);
        assertThat(flags).isEmpty();
    }

    @Test
    void parse_allTypes_parsedCorrectly() {
        Path file = resourcePath("flags-types.yml");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags.get("bool-flag").type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flags.get("string-flag").type()).isEqualTo(FlagType.STRING);
        assertThat(flags.get("number-flag").type()).isEqualTo(FlagType.NUMBER);
        assertThat(flags.get("object-flag").type()).isEqualTo(FlagType.OBJECT);
        assertThat(flags.get("number-flag").value().asNumber()).isEqualTo(42.0);
    }

    @Test
    void parse_invalidType_throwsProviderException() {
        Path file = resourcePath("flags-invalid.yml");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("unknown-type");
    }

    @Test
    void parse_unrecognizedExtension_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.txt");
        Files.writeString(file, "flags:\n  x:\n    type: boolean\n    value: true\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining(".txt")
                .hasMessageContaining("Unrecognized");
    }

    @Test
    void parse_missingTypeField_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    value: true\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("type");
    }

    @Test
    void parse_missingValueField_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    type: boolean\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("value");
    }

    @Test
    void parse_returnsUnmodifiableMap() {
        Path file = resourcePath("flags-valid.yml");
        Map<String, Flag> flags = parser.parse(file);
        assertThatThrownBy(() -> flags.put("new-flag", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void parse_enabledDefaultsToTrue(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    type: boolean\n    value: true\n");
        Flag flag = parser.parse(file).get("my-flag");
        assertThat(flag.enabled()).isTrue();
    }

    private Path resourcePath(String name) {
        try {
            return Path.of(getClass().getClassLoader().getResource(name).toURI());
        } catch (Exception e) {
            throw new RuntimeException("Test resource not found: " + name, e);
        }
    }
}
