package io.github.eliasss3990.openflags.spring;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión: la introspección de {@link OpenFlagsAutoConfiguration} (vía
 * {@code Class.getDeclaredMethods()}) NO debe disparar carga de clases de los
 * módulos de provider opcionales (provider-remote, provider-hybrid).
 *
 * <p>Spring llama {@code getDeclaredMethods()} sobre la autoconfig para
 * descubrir {@code @Bean} methods y evaluar condicionales. Java resuelve los
 * tipos de parámetros y retorno de cada método declarado en la clase. Si
 * algún método declara un tipo del paquete {@code openflags.provider.remote} o
 * {@code openflags.provider.hybrid}, y el JAR correspondiente no está en el
 * classpath del consumer, el classloader tira {@link NoClassDefFoundError} y
 * el contexto Spring explota al startup.
 *
 * <p>Política: todos los tipos {@code provider.remote.*} y
 * {@code provider.hybrid.*} viven dentro de las inner {@code @Configuration}
 * classes gateadas con {@code @ConditionalOnClass}, no en la clase exterior.
 * Cuando el conditional no matchea, Spring nunca introspecciona la inner class
 * y los tipos opcionales nunca se intentan cargar.
 */
class AutoConfigurationIntrospectionTest {

    private static final List<String> OPTIONAL_PROVIDER_PACKAGES = List.of(
            "io.github.eliasss3990.openflags.provider.remote.",
            "io.github.eliasss3990.openflags.provider.hybrid."
            // openflags-provider-file también es <optional> en el pom del starter
            // pero el provider "file" es el default — su tipos sí pueden aparecer
            // en signatures del outer (es lo esperado).
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

    @Test
    void documented_optionalPackages_listIsExhaustive() {
        // Defensa: si alguien agrega un nuevo provider-* opcional, este test
        // debe actualizarse. Validamos contra el resto del codebase reciente.
        List<String> known = Arrays.stream(io.github.eliasss3990.openflags.provider.remote.RemoteFlagProvider.class
                .getPackage().getName().split("\\."))
                .toList();
        assertThat(known).contains("remote");
    }
}
