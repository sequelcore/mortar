package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a named, rendered read query.
 */
public record MortarBoundQuery<T>(
    Optional<String> queryName,
    RenderedQuery rendered,
    Class<T> rowType
) {
    public MortarBoundQuery {
        Objects.requireNonNull(queryName, "queryName cannot be null");
        Objects.requireNonNull(rendered, "rendered cannot be null");
        Objects.requireNonNull(rowType, "rowType cannot be null");
        queryName.ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("queryName cannot be blank");
            }
        });
    }

    public static <T> MortarBoundQuery<T> of(String queryName, RenderedQuery rendered, Class<T> rowType) {
        Objects.requireNonNull(queryName, "queryName cannot be null");
        return new MortarBoundQuery<>(Optional.of(queryName), rendered, rowType);
    }

    public static <T> MortarBoundQuery<T> unnamed(RenderedQuery rendered, Class<T> rowType) {
        return new MortarBoundQuery<>(Optional.empty(), rendered, rowType);
    }

    public MortarBoundQuery<T> named(String queryName) {
        return MortarBoundQuery.of(queryName, rendered, rowType);
    }

    public String sql() {
        return rendered.sql();
    }

    public List<Parameter> parameters() {
        return rendered.parameters();
    }

    public List<Class<?>> parameterTypes() {
        return rendered.parameters().stream()
            .map(Parameter::javaType)
            .toList();
    }

    public QueryMetadata metadata() {
        return rendered.metadata();
    }
}
