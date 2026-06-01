package dev.mortar.jdbc;

import dev.mortar.core.QueryMetadata;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface MortarGeneratedQuery<P, T> {
    String sql();

    default List<Class<?>> parameterTypes() {
        return List.of();
    }

    default QueryMetadata metadata() {
        return QueryMetadata.empty();
    }

    void bind(PreparedStatement statement, P parameters) throws SQLException;

    T map(ResultSet resultSet) throws SQLException;
}
