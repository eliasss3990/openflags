package com.openflags.provider.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileFlagProviderBuilderValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void build_withoutPath_throwsIllegalState() {
        assertThatIllegalStateException()
                .isThrownBy(() -> FileFlagProvider.builder().build())
                .withMessageContaining("path");
    }

    @Test
    void build_pathIsDirectory_throwsIAE() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileFlagProvider.builder().path(tempDir).build())
                .withMessageContaining("directory")
                .withMessageContaining(tempDir.toString());
    }

    @Test
    void build_pathIsNonExistentFile_buildsSuccessfully() {
        Path nonExistent = tempDir.resolve("flags.yml");
        assertThatNoException().isThrownBy(() ->
                FileFlagProvider.builder().path(nonExistent).build());
    }

    @Test
    void path_null_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> FileFlagProvider.builder().path((Path) null));
    }

    @Test
    void path_nullString_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> FileFlagProvider.builder().path((String) null));
    }
}
