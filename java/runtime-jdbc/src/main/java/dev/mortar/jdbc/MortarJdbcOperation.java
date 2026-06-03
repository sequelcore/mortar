package dev.mortar.jdbc;

/**
 * JDBC operation categories used in log events.
 */
public enum MortarJdbcOperation {
    QUERY,
    MUTATION,
    BATCH
}
