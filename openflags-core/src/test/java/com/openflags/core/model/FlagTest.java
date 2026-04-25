package com.openflags.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FlagTest {

    private static final FlagValue BOOL_VALUE = FlagValue.of(true, FlagType.BOOLEAN);

    @Test
    void constructor_createsValidFlag() {
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        assertThat(flag.key()).isEqualTo("my-flag");
        assertThat(flag.type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flag.value()).isEqualTo(BOOL_VALUE);
        assertThat(flag.enabled()).isTrue();
    }

    @Test
    void constructor_nullMetadataDefaultsToEmptyMap() {
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, null);
        assertThat(flag.metadata()).isEmpty();
    }

    @Test
    void constructor_metadataIsUnmodifiable() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("owner", "team-a");
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, mutable);
        assertThatThrownBy(() -> flag.metadata().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_throwsWhenKeyIsNull() {
        assertThatThrownBy(() -> new Flag(null, FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenKeyIsBlank() {
        assertThatThrownBy(() -> new Flag("  ", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsWhenKeyHasInvalidPattern() {
        assertThatThrownBy(() -> new Flag("1invalid", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag("has space", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag("has@special", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsValidKeyPatterns() {
        assertThatCode(() -> new Flag("a", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Flag("my-flag.v2", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Flag("feature_123", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
    }

    @Test
    void constructor_throwsWhenTypeIsNull() {
        assertThatThrownBy(() -> new Flag("my-flag", null, BOOL_VALUE, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenValueIsNull() {
        assertThatThrownBy(() -> new Flag("my-flag", FlagType.BOOLEAN, null, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsOnTypeMismatch() {
        FlagValue stringValue = FlagValue.of("text", FlagType.STRING);
        assertThatThrownBy(() -> new Flag("my-flag", FlagType.BOOLEAN, stringValue, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRING")
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        Flag f1 = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        Flag f2 = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        Flag f3 = new Flag("other-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        assertThat(f1).isEqualTo(f2).hasSameHashCodeAs(f2);
        assertThat(f1).isNotEqualTo(f3);
    }
}
