package com.openflags.provider.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagFileParserPublicApiTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private FlagFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlagFileParser();
    }

    @Test
    void parseFlags_withValidJsonNode_returnsFlagMap() throws Exception {
        String json = """
                {
                  "flags": {
                    "dark-mode": {
                      "type": "boolean",
                      "value": true,
                      "enabled": true
                    }
                  }
                }
                """;
        JsonNode root = MAPPER.readTree(json);
        Map<String, Flag> flags = parser.parseFlags(root, "test-source");

        assertThat(flags).containsKey("dark-mode");
        assertThat(flags.get("dark-mode").type()).isEqualTo(FlagType.BOOLEAN);
    }

    @Test
    void parseFlags_withMultivariantRule_parsesCorrectly() throws Exception {
        String json = """
                {
                  "flags": {
                    "checkout-experiment": {
                      "type": "string",
                      "value": "control",
                      "enabled": true,
                      "rules": [
                        {
                          "name": "ab-test",
                          "kind": "multivariant",
                          "variants": [
                            { "value": "control",   "weight": 50 },
                            { "value": "treatment", "weight": 50 }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;
        JsonNode root = MAPPER.readTree(json);
        Map<String, Flag> flags = parser.parseFlags(root, "remote:https://flags.example.com");

        assertThat(flags).containsKey("checkout-experiment");
        assertThat(flags.get("checkout-experiment").rules()).hasSize(1);
    }

    @Test
    void parseFlags_withEmptyFlagsObject_returnsEmptyMap() throws Exception {
        JsonNode root = MAPPER.readTree("{\"flags\":{}}");
        Map<String, Flag> flags = parser.parseFlags(root, "test");
        assertThat(flags).isEmpty();
    }

    @Test
    void parseFlags_missingFlagsKey_throwsProviderException() throws Exception {
        JsonNode root = MAPPER.readTree("{\"other\":{}}");
        assertThatThrownBy(() -> parser.parseFlags(root, "test-source"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("'flags'");
    }

    @Test
    void parseFlags_nullRoot_throwsProviderException() {
        assertThatThrownBy(() -> parser.parseFlags(null, "test-source"))
                .isInstanceOf(ProviderException.class);
    }
}
