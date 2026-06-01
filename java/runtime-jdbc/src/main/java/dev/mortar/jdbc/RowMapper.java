package dev.mortar.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
/**
 * Callback for mapping a JDBC result-set row to an application value.
 */
public interface RowMapper<T> {
    T map(ResultSet resultSet) throws SQLException;
}
