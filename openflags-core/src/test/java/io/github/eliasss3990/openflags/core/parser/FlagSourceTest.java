package io.github.eliasss3990.openflags.core.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FlagSourceTest {

    @Test
    void filePrefixMatchesParserConvention() {
        assertThat(FlagSource.FILE.prefix()).isEqualTo("file");
        assertThat(FlagSource.FILE.label("flags.yml")).isEqualTo("file:flags.yml");
    }

    @Test
    void remotePrefixMatchesParserConvention() {
        assertThat(FlagSource.REMOTE.prefix()).isEqualTo("remote");
        assertThat(FlagSource.REMOTE.label("https://example.com"))
                .isEqualTo("remote:https://example.com");
    }

    @Test
    void labelRejectsNullDetail() {
        assertThatNullPointerException().isThrownBy(() -> FlagSource.FILE.label(null));
    }
}
