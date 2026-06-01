package dev.mortar.jdbc;

@FunctionalInterface
public interface MortarJdbcLogger {
    void log(MortarJdbcLogEvent event);

    static MortarJdbcLogger noop() {
        return event -> {
        };
    }
}
