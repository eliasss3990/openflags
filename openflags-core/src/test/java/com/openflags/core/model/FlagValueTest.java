package com.openflags.core.model;

import com.openflags.core.exception.TypeMismatchException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FlagValueTest {

    @Test
    void ofBoolean_createsValue() {
        FlagValue value = FlagValue.of(true, FlagType.BOOLEAN);
        assertThat(value.getType()).isEqualTo(FlagType.BOOLEAN);
        assertThat(value.asBoolean()).isTrue();
    }

    @Test
    void ofString_createsValue() {
        FlagValue value = FlagValue.of("hello", FlagType.STRING);
        assertThat(value.getType()).isEqualTo(FlagType.STRING);
        assertThat(value.asString()).isEqualTo("hello");
    }

    @Test
    void ofNumber_createsValue() {
        FlagValue value = FlagValue.of(3.14, FlagType.NUMBER);
        assertThat(value.getType()).isEqualTo(FlagType.NUMBER);
        assertThat(value.asNumber()).isEqualTo(3.14);
    }

    @Test
    void ofObject_createsValue() {
        Map<String, Object> map = Map.of("key", "val");
        FlagValue value = FlagValue.of(map, FlagType.OBJECT);
        assertThat(value.getType()).isEqualTo(FlagType.OBJECT);
        assertThat(value.asObject()).containsEntry("key", "val");
    }

    @Test
    void asObject_returnsUnmodifiableMap() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("x", 1);
        FlagValue value = FlagValue.of(mutable, FlagType.OBJECT);
        Map<String, Object> result = value.asObject();
        assertThatThrownBy(() -> result.put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_throwsOnTypeMismatch() {
        assertThatThrownBy(() -> FlagValue.of("not-a-bool", FlagType.BOOLEAN))
                .isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> FlagValue.of(42, FlagType.STRING))
                .isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> FlagValue.of("text", FlagType.NUMBER))
                .isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> FlagValue.of(true, FlagType.OBJECT))
                .isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void asBoolean_throwsWhenNotBoolean() {
        FlagValue value = FlagValue.of("text", FlagType.STRING);
        assertThatThrownBy(value::asBoolean).isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void asString_throwsWhenNotString() {
        FlagValue value = FlagValue.of(true, FlagType.BOOLEAN);
        assertThatThrownBy(value::asString).isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void asNumber_throwsWhenNotNumber() {
        FlagValue value = FlagValue.of("text", FlagType.STRING);
        assertThatThrownBy(value::asNumber).isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void asObject_throwsWhenNotObject() {
        FlagValue value = FlagValue.of(true, FlagType.BOOLEAN);
        assertThatThrownBy(value::asObject).isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void of_throwsWhenTypeIsNull() {
        assertThatThrownBy(() -> FlagValue.of(true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        FlagValue v1 = FlagValue.of(true, FlagType.BOOLEAN);
        FlagValue v2 = FlagValue.of(true, FlagType.BOOLEAN);
        FlagValue v3 = FlagValue.of(false, FlagType.BOOLEAN);
        assertThat(v1).isEqualTo(v2).hasSameHashCodeAs(v2);
        assertThat(v1).isNotEqualTo(v3);
    }

    @Test
    void getRawValue_returnsUnderlyingValue() {
        FlagValue value = FlagValue.of(99.0, FlagType.NUMBER);
        assertThat(value.getRawValue()).isEqualTo(99.0);
    }
}
