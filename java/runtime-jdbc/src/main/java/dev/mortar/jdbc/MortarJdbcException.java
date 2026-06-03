package dev.mortar.jdbc;

import dev.mortar.core.Parameter;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.RenderedQuery;

import java.util.List;
import java.util.Objects;

/**
 * Unchecked JDBC failure with rendered SQL context when available.
 */
public final class MortarJdbcException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String sql;
    private final transient List<Parameter> parameters;
    private final transient QueryMetadata metadata;

    /**
     * Creates an exception without rendered SQL context.
     */
    public MortarJdbcException(String message, Throwable cause) {
        super(message, cause);
        this.sql = "";
        this.parameters = List.of();
        this.metadata = QueryMetadata.empty();
    }

    /**
     * Creates an exception that retains rendered SQL, parameters, and metadata
     * for diagnostics.
     */
    public MortarJdbcException(String message, RenderedQuery renderedQuery, Throwable cause) {
        super(message, cause);
        Objects.requireNonNull(renderedQuery, "renderedQuery cannot be null");
        this.sql = renderedQuery.sql();
        this.parameters = renderedQuery.parameters();
        this.metadata = renderedQuery.metadata();
    }

    /**
     * Returns the SQL that failed, or an empty string when unavailable.
     */
    public String sql() {
        return sql;
    }

    /**
     * Returns rendered parameters associated with the failed SQL.
     */
    public List<Parameter> parameters() {
        return parameters;
    }

    /**
     * Returns metadata associated with the failed SQL.
     */
    public QueryMetadata metadata() {
        return metadata;
    }
}
