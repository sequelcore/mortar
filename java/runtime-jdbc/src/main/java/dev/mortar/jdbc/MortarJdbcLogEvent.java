package dev.mortar.jdbc;

import dev.mortar.core.QueryMetadata;

import java.util.List;
import java.util.Objects;

public record MortarJdbcLogEvent(
    MortarJdbcOperation operation,
    String sql,
    List<MortarJdbcParameter> parameters,
    QueryMetadata metadata
) {
    public MortarJdbcLogEvent {
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(sql, "sql cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");
        Objects.requireNonNull(metadata, "metadata cannot be null");
        parameters = List.copyOf(parameters);
    }
}
