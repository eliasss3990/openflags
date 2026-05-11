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
 * Regression test: when {@code openflags.file.path=classpath:flags.yml} and
 * the resource lives <b>inside a JAR</b>:
 *
 * <ul>
 *   <li>with {@code watch=false} → the starter extracts the file to a temp
 *       location and starts normally. The common Spring Boot launcher JAR
 *       case.</li>
 *   <li>with {@code watch=true} → fail-fast with an actionable error —
 *       WatchService cannot watch entries inside an archive.</li>
 * </ul>
 *
 * <p>The "resource inside JAR" scenario is reproduced by building a temp
 * JAR containing {@code flags.yml} and adding it to the context runner's
 * classloader.
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
        // The extracted temp file must keep the .yml extension so that the
        // FileFlagProvider parser treats it as YAML and not as JSON.
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
     * Builds a child classloader whose only additional resource is a temp JAR
     * containing {@code resourceName} with the given content. A lookup of
     * {@code classpath:resourceName} against that classloader returns a
     * {@code Resource} whose URI has scheme {@code jar:} — exactly the
     * scenario that fails in production with the Spring Boot launcher JAR.
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
