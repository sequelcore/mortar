package dev.mortar.spring;

import dev.mortar.postgres.PostgresSqlFormat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mortar")
public class MortarSpringProperties {
    private PostgresSqlFormat sqlFormat = PostgresSqlFormat.COMPACT;
    private final Jdbc jdbc = new Jdbc();
    private final Diagnostics diagnostics = new Diagnostics();

    public PostgresSqlFormat getSqlFormat() {
        return sqlFormat;
    }

    public void setSqlFormat(PostgresSqlFormat sqlFormat) {
        this.sqlFormat = sqlFormat;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public static final class Jdbc {
        private final Logging logging = new Logging();

        public Logging getLogging() {
            return logging;
        }
    }

    public static final class Logging {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static final class Diagnostics {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
