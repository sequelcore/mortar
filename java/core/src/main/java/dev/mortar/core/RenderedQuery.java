package dev.mortar.core;

import java.util.List;
import java.util.Objects;

/**
 * SQL text, bound parameters, and metadata produced by a renderer.
 */
public record RenderedQuery(String sql, List<Parameter> parameters, QueryMetadata metadata) {
    public RenderedQuery(String sql, List<Parameter> parameters) {
        this(sql, parameters, QueryMetadata.empty());
    }

    public RenderedQuery {
        Objects.requireNonNull(sql, "sql cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");
        Objects.requireNonNull(metadata, "metadata cannot be null");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("sql cannot be blank");
        }
        parameters = List.copyOf(parameters);
    }
}
