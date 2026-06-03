package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a rendered read query and its row type.
 *
 * <p>A bound query is inspectable SQL plus metadata. It is not self-executing;
 * runtime adapters such as the JDBC module own execution.</p>
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

    /**
     * Creates a named bound read query.
     *
     * @throws NullPointerException when {@code queryName}, {@code rendered}, or
     * {@code rowType} is null
     * @throws IllegalArgumentException when {@code queryName} is blank
     */
    public static <T> MortarBoundQuery<T> of(String queryName, RenderedQuery rendered, Class<T> rowType) {
        Objects.requireNonNull(queryName, "queryName cannot be null");
        return new MortarBoundQuery<>(Optional.of(queryName), rendered, rowType);
    }

    /**
     * Creates an unnamed bound read query for later naming or direct execution.
     */
    public static <T> MortarBoundQuery<T> unnamed(RenderedQuery rendered, Class<T> rowType) {
        return new MortarBoundQuery<>(Optional.empty(), rendered, rowType);
    }

    /**
     * Returns a copy with an inspection name.
     */
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
