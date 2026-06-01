package dev.mortar.spring;

import dev.mortar.postgres.PostgresSqlFormat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties bound from the {@code mortar.*} Spring Boot namespace.
 */
@ConfigurationProperties(prefix = "mortar")
public class MortarSpringProperties {
    private MortarDialect dialect = MortarDialect.POSTGRES;
    private PostgresSqlFormat sqlFormat = PostgresSqlFormat.COMPACT;
    private final Jdbc jdbc = new Jdbc();
    private final Diagnostics diagnostics = new Diagnostics();

    /**
     * Creates properties with Mortar's documented starter defaults.
     */
    public MortarSpringProperties() {
    }

    /**
     * Returns the configured dialect.
     *
     * @return configured dialect
     */
    public MortarDialect getDialect() {
        return dialect;
    }

    /**
     * Sets the configured dialect.
     *
     * @param dialect configured dialect
     */
    public void setDialect(MortarDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Returns the configured PostgreSQL SQL format.
     *
     * @return SQL format
     */
    public PostgresSqlFormat getSqlFormat() {
        return sqlFormat;
    }

    /**
     * Sets the configured PostgreSQL SQL format.
     *
     * @param sqlFormat SQL format
     */
    public void setSqlFormat(PostgresSqlFormat sqlFormat) {
        this.sqlFormat = sqlFormat;
    }

    /**
     * Returns JDBC-related properties.
     *
     * @return JDBC properties
     */
    public Jdbc getJdbc() {
        return jdbc;
    }

    /**
     * Returns diagnostics properties.
     *
     * @return diagnostics properties
     */
    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    /**
     * JDBC-related starter properties.
     */
    public static final class Jdbc {
        private final Logging logging = new Logging();

        /**
         * Creates JDBC properties with documented defaults.
         */
        public Jdbc() {
        }

        /**
         * Returns SQL logging properties.
         *
         * @return logging properties
         */
        public Logging getLogging() {
            return logging;
        }
    }

    /**
     * SQL logging properties for the JDBC adapter.
     */
    public static final class Logging {
        private boolean enabled;

        /**
         * Creates logging properties with logging disabled.
         */
        public Logging() {
        }

        /**
         * Returns whether JDBC SQL logging is enabled.
         *
         * @return true when logging is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether JDBC SQL logging is enabled.
         *
         * @param enabled true to enable logging
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Diagnostics endpoint properties.
     */
    public static final class Diagnostics {
        private boolean enabled = true;

        /**
         * Creates diagnostics properties with diagnostics enabled.
         */
        public Diagnostics() {
        }

        /**
         * Returns whether actuator diagnostics are enabled.
         *
         * @return true when diagnostics are enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether actuator diagnostics are enabled.
         *
         * @param enabled true to enable diagnostics
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
