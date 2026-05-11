package io.github.eliasss3990.openflags.spring;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: introspection of {@link OpenFlagsAutoConfiguration} (via
 * {@code Class.getDeclaredMethods()}) MUST NOT trigger class loading of the
 * optional provider modules (provider-remote, provider-hybrid).
 *
 * <p>Spring calls {@code getDeclaredMethods()} on the auto-config to discover
 * {@code @Bean} methods and evaluate conditionals. Java resolves the parameter
 * and return types of every declared method. If any method declares a type
 * from the {@code openflags.provider.remote} or {@code openflags.provider.hybrid}
 * packages and the corresponding JAR is not on the consumer classpath, the
 * classloader throws {@link NoClassDefFoundError} and the Spring context
 * fails to start.
 *
 * <p>Policy: every {@code provider.remote.*} and {@code provider.hybrid.*}
 * type lives inside an inner {@code @Configuration} class gated by
 * {@code @ConditionalOnClass}, not in the outer class. When the conditional
 * does not match, Spring never introspects the inner class and the optional
 * types are never loaded.
 */
class AutoConfigurationIntrospectionTest {

    private static final List<String> OPTIONAL_PROVIDER_PACKAGES = List.of(
            "io.github.eliasss3990.openflags.provider.remote.",
            "io.github.eliasss3990.openflags.provider.hybrid."
            // openflags-provider-file is also <optional> in the starter POM,
            // but "file" is the default provider — its types are expected to
            // appear in the outer class signatures.
    );

    @Test
    void outerClassMethods_doNotReferenceOptionalProviderTypes() {
        Method[] methods = OpenFlagsAutoConfiguration.class.getDeclaredMethods();
        for (Method m : methods) {
            assertTypeNotOptional(m.getReturnType(), m, "return type");
            for (Class<?> param : m.getParameterTypes()) {
                assertTypeNotOptional(param, m, "parameter");
            }
        }
    }

    @Test
    void outerClassFields_doNotReferenceOptionalProviderTypes() {
        for (var f : OpenFlagsAutoConfiguration.class.getDeclaredFields()) {
            assertTypeNotOptional(f.getType(), null, "field " + f.getName());
        }
    }

    private static void assertTypeNotOptional(Class<?> type, Method method, String where) {
        Stream<Class<?>> chain = Stream.iterate(type, c -> c != null, Class::getComponentType);
        chain.forEach(c -> {
            String name = c.getName();
            boolean violates = OPTIONAL_PROVIDER_PACKAGES.stream().anyMatch(name::startsWith);
            String label = method != null
                    ? "method '" + method.getName() + "' " + where
                    : where;
            assertThat(violates)
                    .as("%s in OpenFlagsAutoConfiguration references optional provider type %s. "
                            + "Move this method/field into the inner @Configuration class gated by "
                            + "@ConditionalOnClass for that provider.",
                            label, name)
                    .isFalse();
        });
    }
}
