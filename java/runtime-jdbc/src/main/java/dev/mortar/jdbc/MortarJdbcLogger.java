package dev.mortar.jdbc;

@FunctionalInterface
/**
 * Logging boundary for applications that want visibility into executed SQL.
 */
public interface MortarJdbcLogger {
    void log(MortarJdbcLogEvent event);

    static MortarJdbcLogger noop() {
        return event -> {
        };
    }
}
