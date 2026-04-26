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
}
