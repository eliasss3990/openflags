package io.github.eliasss3990.openflags.spring;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import io.github.eliasss3990.openflags.core.provider.FlagProvider;
import io.github.eliasss3990.openflags.provider.file.FileFlagProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión: cuando {@code openflags.file.path=classpath:flags.yml} y el
 * recurso vive <b>dentro de un JAR</b>:
 *
 * <ul>
 *   <li>con {@code watch=false} → el starter extrae el archivo a un temp file
 *       y arranca normal. Caso common de Spring Boot launcher JAR.</li>
 *   <li>con {@code watch=true} → fail-fast con mensaje accionable —
 *       WatchService no soporta entries dentro de un archivo.</li>
 * </ul>
 *
 * <p>Reproducimos el escenario "recurso dentro de JAR" creando un JAR temporal
 * con {@code flags.yml} adentro y agregándolo al classpath del context runner.
 */
class FileProviderClasspathInJarTest {

    private static final String FLAGS_YAML = """
            flags:
              demo.flag:
                type: boolean
                value: true
                enabled: true
            """;

    @Test
    void classpathInsideJar_withWatchOff_extractsAndStarts(@TempDir Path tmp) throws Exception {
        URLClassLoader cl = classLoaderWithJarContaining("flags-in-jar.yml", FLAGS_YAML, tmp);

        new ApplicationContextRunner()
                .withClassLoader(cl)
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=file",
                        "openflags.file.path=classpath:flags-in-jar.yml",
                        "openflags.file.watch-enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    FlagProvider provider = ctx.getBean(FlagProvider.class);
                    assertThat(provider).isInstanceOf(FileFlagProvider.class);
                    OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
                    assertThat(client.getBooleanValue("demo.flag", false))
                            .as("the flag bundled inside the test JAR must be readable after extraction")
                            .isTrue();
                });
    }

    @Test
    void classpathInsideJar_withWatchOn_failsWithActionableMessage(@TempDir Path tmp) throws Exception {
        URLClassLoader cl = classLoaderWithJarContaining("flags-in-jar.yml", FLAGS_YAML, tmp);

        new ApplicationContextRunner()
                .withClassLoader(cl)
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=file",
                        "openflags.file.path=classpath:flags-in-jar.yml",
                        "openflags.file.watch-enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IOException.class)
                            .hasMessageContaining("inside a JAR")
                            .hasMessageContaining("openflags.file.watch")
                            .hasMessageContaining("file:");
                });
    }

    @Test
    void classpathExtractionHelper_preservesYamlExtension(@TempDir Path tmp) throws Exception {
        // El temp file extraído debe terminar en .yml para que el parser
        // YAML del FileFlagProvider lo trate como YAML y no como JSON.
        URLClassLoader cl = classLoaderWithJarContaining("custom-flags.yml", FLAGS_YAML, tmp);

        new ApplicationContextRunner()
                .withClassLoader(cl)
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=file",
                        "openflags.file.path=classpath:custom-flags.yml",
                        "openflags.file.watch-enabled=false")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    /**
     * Construye un classloader hijo cuyo único recurso adicional es un JAR
     * temporal que contiene {@code resourceName} con el contenido dado. La
     * lookup vía {@code classpath:resourceName} a ese classloader devuelve
     * un {@code Resource} cuya URI tiene scheme {@code jar:} — exactamente
     * el escenario que rompe en producción con el launcher JAR de Spring Boot.
     */
    private static URLClassLoader classLoaderWithJarContaining(String resourceName, String content, Path tmp)
            throws Exception {
        Path jarPath = tmp.resolve("resources.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (var fos = Files.newOutputStream(jarPath);
                JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            jos.putNextEntry(new ZipEntry(resourceName));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return new URLClassLoader(
                "openflags-test-jar",
                new URL[] { jarPath.toUri().toURL() },
                Thread.currentThread().getContextClassLoader());
    }
}
