package dev.mortar.jdbc;

import dev.mortar.core.QueryMetadata;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Generated query contract with pre-rendered SQL, direct parameter binding, and row mapping.
 *
 * <p>Generated queries are execution-ready descriptions. They expose SQL and
 * metadata for inspection while keeping execution in {@link MortarJdbcClient}
 * or {@link MortarPreparedQuery}.</p>
 */
public interface MortarGeneratedQuery<P, T> {
    /**
     * Returns the rendered SQL text.
     */
    String sql();

    /**
     * Returns the ordered Java parameter types expected by {@link #bind}.
     */
    default List<Class<?>> parameterTypes() {
        return List.of();
    }

    /**
     * Returns table, column, and join metadata for diagnostics and testkit
     * assertions.
     */
    default QueryMetadata metadata() {
        return QueryMetadata.empty();
    }

    /**
     * Binds generated-query parameters into a prepared statement.
     *
     * @throws SQLException when the JDBC driver rejects a bind operation
     */
    void bind(PreparedStatement statement, P parameters) throws SQLException;

    /**
     * Maps the current result-set row into the generated row type.
     *
     * @throws SQLException when the JDBC driver rejects a read operation
     */
    T map(ResultSet resultSet) throws SQLException;
}
