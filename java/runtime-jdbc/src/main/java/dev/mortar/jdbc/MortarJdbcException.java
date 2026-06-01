package dev.mortar.jdbc;

import dev.mortar.core.Parameter;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.RenderedQuery;

import java.util.List;
import java.util.Objects;

public final class MortarJdbcException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String sql;
    private final transient List<Parameter> parameters;
    private final transient QueryMetadata metadata;

    public MortarJdbcException(String message, Throwable cause) {
        super(message, cause);
        this.sql = "";
        this.parameters = List.of();
        this.metadata = QueryMetadata.empty();
    }

    public MortarJdbcException(String message, RenderedQuery renderedQuery, Throwable cause) {
        super(message, cause);
        Objects.requireNonNull(renderedQuery, "renderedQuery cannot be null");
        this.sql = renderedQuery.sql();
        this.parameters = renderedQuery.parameters();
        this.metadata = renderedQuery.metadata();
    }

    public String sql() {
        return sql;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public QueryMetadata metadata() {
        return metadata;
    }
}
