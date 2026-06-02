package dev.mortar.jdbc;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.Parameter;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.RenderedQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC row-mapping adapter for a framework-free bound read query.
 */
public record MortarJdbcBoundQuery<T>(
    MortarBoundQuery<T> boundQuery,
    RowMapper<T> rowMapper
) {
    public MortarJdbcBoundQuery {
        Objects.requireNonNull(boundQuery, "boundQuery cannot be null");
        Objects.requireNonNull(rowMapper, "rowMapper cannot be null");
    }

    public static <T> MortarJdbcBoundQuery<T> of(MortarBoundQuery<T> boundQuery, RowMapper<T> rowMapper) {
        return new MortarJdbcBoundQuery<>(boundQuery, rowMapper);
    }

    public MortarJdbcBoundQuery<T> named(String queryName) {
        return MortarJdbcBoundQuery.of(boundQuery.named(queryName), rowMapper);
    }

    public Optional<String> queryName() {
        return boundQuery.queryName();
    }

    public RenderedQuery rendered() {
        return boundQuery.rendered();
    }

    public String sql() {
        return boundQuery.sql();
    }

    public List<Parameter> parameters() {
        return boundQuery.parameters();
    }

    public List<Class<?>> parameterTypes() {
        return boundQuery.parameterTypes();
    }

    public QueryMetadata metadata() {
        return boundQuery.metadata();
    }

    public Class<T> rowType() {
        return boundQuery.rowType();
    }
}
