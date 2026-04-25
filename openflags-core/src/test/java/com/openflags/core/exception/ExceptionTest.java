package com.openflags.core.exception;

import com.openflags.core.model.FlagType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExceptionTest {

    @Test
    void openFlagsException_isRuntimeException() {
        assertThat(new OpenFlagsException("msg")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void openFlagsException_withCause() {
        Throwable cause = new IllegalStateException("root");
        OpenFlagsException ex = new OpenFlagsException("msg", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("msg");
    }

    @Test
    void flagNotFoundException_extendsBase() {
        FlagNotFoundException ex = new FlagNotFoundException("dark-mode");
        assertThat(ex).isInstanceOf(OpenFlagsException.class);
        assertThat(ex.getFlagKey()).isEqualTo("dark-mode");
        assertThat(ex.getMessage()).contains("dark-mode");
    }

    @Test
    void typeMismatchException_extendsBase() {
        TypeMismatchException ex = new TypeMismatchException("my-flag", FlagType.BOOLEAN, FlagType.STRING);
        assertThat(ex).isInstanceOf(OpenFlagsException.class);
        assertThat(ex.getFlagKey()).isEqualTo("my-flag");
        assertThat(ex.getExpectedType()).isEqualTo(FlagType.BOOLEAN);
        assertThat(ex.getActualType()).isEqualTo(FlagType.STRING);
        assertThat(ex.getMessage()).contains("my-flag").contains("BOOLEAN").contains("STRING");
    }

    @Test
    void typeMismatchException_withNullKey() {
        TypeMismatchException ex = new TypeMismatchException(null, FlagType.NUMBER, FlagType.STRING);
        assertThat(ex.getFlagKey()).isNull();
        assertThat(ex.getMessage()).contains("NUMBER").contains("STRING");
    }

    @Test
    void providerException_extendsBase() {
        ProviderException ex = new ProviderException("IO error");
        assertThat(ex).isInstanceOf(OpenFlagsException.class);
        assertThat(ex.getMessage()).isEqualTo("IO error");
    }

    @Test
    void providerException_withCause() {
        Throwable cause = new RuntimeException("root");
        ProviderException ex = new ProviderException("parse error", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
