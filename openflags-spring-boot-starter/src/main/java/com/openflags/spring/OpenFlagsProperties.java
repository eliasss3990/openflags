package com.openflags.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the openflags Spring Boot integration.
 * <p>
 * Bound to the {@code openflags} prefix in {@code application.yml} or
 * {@code application.properties}.
 * </p>
 *
 * <pre>
 * openflags:
 *   provider: file
 *   file:
 *     path: classpath:flags.yml
 *     watch-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "openflags")
public class OpenFlagsProperties {

    /** The provider type to activate. Default: {@code "file"}. */
    private String provider = "file";

    /** File provider configuration. */
    private FileProperties file = new FileProperties();

    /**
     * Returns the active provider type.
     *
     * @return the provider type string
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the active provider type.
     *
     * @param provider the provider type
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Returns the file provider configuration.
     *
     * @return the file properties
     */
    public FileProperties getFile() {
        return file;
    }

    /**
     * Sets the file provider configuration.
     *
     * @param file the file properties
     */
    public void setFile(FileProperties file) {
        this.file = file;
    }

    /**
     * File provider specific configuration.
     */
    public static class FileProperties {

        /**
         * Path to the flags definition file.
         * Supports {@code classpath:} and {@code file:} prefixes.
         * Default: {@code "classpath:flags.yml"}.
         */
        private String path = "classpath:flags.yml";

        /**
         * Whether to enable hot reload via file watching.
         * Automatically disabled when the file is inside a JAR.
         * Default: {@code true}.
         */
        private boolean watchEnabled = true;

        /**
         * Returns the path to the flag file.
         *
         * @return the path string
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the path to the flag file.
         *
         * @param path the path string
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns whether file watching is enabled.
         *
         * @return {@code true} if watch is enabled
         */
        public boolean isWatchEnabled() {
            return watchEnabled;
        }

        /**
         * Sets whether file watching is enabled.
         *
         * @param watchEnabled {@code true} to enable
         */
        public void setWatchEnabled(boolean watchEnabled) {
            this.watchEnabled = watchEnabled;
        }
    }
}
