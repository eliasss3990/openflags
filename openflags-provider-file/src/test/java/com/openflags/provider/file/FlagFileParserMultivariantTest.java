package com.openflags.provider.file;

import com.openflags.core.evaluation.rule.MultiVariantRule;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagFileParserMultivariantTest {

    private FlagFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlagFileParser();
    }

    private Path resourcePath(String name) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(name).toURI());
    }

    @Test
    void parse_multivariantAbc_threeVariants() throws Exception {
        Map<String, Flag> flags = parser.parse(resourcePath("flags-multivariant-abc.yml"));

        assertThat(flags).containsKey("checkout-experiment");
        Flag flag = flags.get("checkout-experiment");
        assertThat(flag.type()).isEqualTo(FlagType.STRING);
        assertThat(flag.rules()).hasSize(1);
        assertThat(flag.rules().get(0)).isInstanceOf(MultiVariantRule.class);

        MultiVariantRule rule = (MultiVariantRule) flag.rules().get(0);
        assertThat(rule.name()).isEqualTo("abc-test");
        assertThat(rule.variants()).hasSize(3);
        assertThat(rule.variants().get(0).weight()).isEqualTo(50);
        assertThat(rule.variants().get(1).weight()).isEqualTo(25);
        assertThat(rule.variants().get(2).weight()).isEqualTo(25);
    }

    @Test
    void parse_multivariantMixed_targetingAndMultivariant() throws Exception {
        Map<String, Flag> flags = parser.parse(resourcePath("flags-multivariant-mixed.yml"));

        assertThat(flags).containsKey("new-search-ranking");
        Flag flag = flags.get("new-search-ranking");
        assertThat(flag.rules()).hasSize(2);
        assertThat(flag.rules().get(1)).isInstanceOf(MultiVariantRule.class);

        MultiVariantRule mvr = (MultiVariantRule) flag.rules().get(1);
        assertThat(mvr.variants()).hasSize(2);
        assertThat(mvr.variants().get(0).weight()).isEqualTo(70);
        assertThat(mvr.variants().get(1).weight()).isEqualTo(30);
    }

    @Test
    void parse_badWeightSum_throwsProviderException() throws Exception {
        Path file = resourcePath("flags-multivariant-bad-sum.yml");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("80")
                .hasMessageContaining("100");
    }

    @Test
    void parse_weightOutOfRange_throwsProviderException() throws Exception {
        Path file = resourcePath("flags-multivariant-bad-weight.yml");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("150")
                .hasMessageContaining("[0, 100]");
    }

    @Test
    void weightAcceptsIntegralDouble() throws Exception {
        Map<String, Flag> flags = parser.parse(resourcePath("flags-multivariant-float-weight.yml"));
        assertThat(flags).containsKey("float-weight-flag");
        MultiVariantRule rule = (MultiVariantRule) flags.get("float-weight-flag").rules().get(0);
        assertThat(rule.variants().get(0).weight()).isEqualTo(50);
        assertThat(rule.variants().get(1).weight()).isEqualTo(50);
    }

    @Test
    void weightRejectsFractional(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        String yaml = "flags:\n  f:\n    type: string\n    value: a\n    rules:\n"
                + "      - name: r\n        kind: multivariant\n        variants:\n"
                + "          - value: a\n            weight: 49.5\n"
                + "          - value: b\n            weight: 50.5\n";
        java.nio.file.Path file = tempDir.resolve("frac.yml");
        java.nio.file.Files.writeString(file, yaml);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("non-integer weight");
    }
}
