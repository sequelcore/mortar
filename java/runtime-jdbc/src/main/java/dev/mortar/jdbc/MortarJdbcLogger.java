package dev.mortar.jdbc;

/**
 * Logging boundary for applications that want visibility into executed SQL.
 */
@FunctionalInterface
public interface MortarJdbcLogger {
    void log(MortarJdbcLogEvent event);

    static MortarJdbcLogger noop() {
        return event -> {
        };
    }
}
