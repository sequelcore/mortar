package dev.mortar.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Callback for mapping a JDBC result-set row to an application value.
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * Maps the current result-set row.
     *
     * @throws SQLException when the JDBC driver rejects a read operation
     */
    T map(ResultSet resultSet) throws SQLException;
}
