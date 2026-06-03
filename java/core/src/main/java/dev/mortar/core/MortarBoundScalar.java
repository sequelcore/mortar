package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a rendered scalar query and its Java result
 * type.
 *
 * <p>A bound scalar is inspectable SQL plus metadata. It is not
 * self-executing; runtime adapters decide how to fetch and convert the single
 * value.</p>
 */
public record MortarBoundScalar<T>(
    Optional<String> queryName,
    RenderedQuery rendered,
    Class<T> scalarType
) {
    public MortarBoundScalar {
        Objects.requireNonNull(queryName, "queryName cannot be null");
        Objects.requireNonNull(rendered, "rendered cannot be null");
        Objects.requireNonNull(scalarType, "scalarType cannot be null");
        queryName.ifPresent(MortarBoundScalar::requireName);
    }

    /**
     * Creates a named bound scalar from already rendered SQL.
     */
    public static <T> MortarBoundScalar<T> of(String queryName, RenderedQuery rendered, Class<T> scalarType) {
        return new MortarBoundScalar<>(Optional.of(requireName(queryName)), rendered, scalarType);
    }

    /**
     * Renders and names a scalar specification.
     */
    public static <T> MortarBoundScalar<T> of(String queryName, ScalarSpec<T> scalar, QueryRenderer renderer) {
        return unnamed(scalar, renderer).named(queryName);
    }

    /**
     * Creates an unnamed bound scalar from already rendered SQL.
     */
    public static <T> MortarBoundScalar<T> unnamed(RenderedQuery rendered, Class<T> scalarType) {
        return new MortarBoundScalar<>(Optional.empty(), rendered, scalarType);
    }

    /**
     * Renders an unnamed scalar specification.
     */
    public static <T> MortarBoundScalar<T> unnamed(ScalarSpec<T> scalar, QueryRenderer renderer) {
        Objects.requireNonNull(scalar, "scalar cannot be null");
        Objects.requireNonNull(renderer, "renderer cannot be null");
        return new MortarBoundScalar<>(Optional.empty(), render(scalar, renderer), scalar.scalarType());
    }

    /**
     * Returns a copy with an inspection name.
     */
    public MortarBoundScalar<T> named(String queryName) {
        return MortarBoundScalar.of(queryName, rendered, scalarType);
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

    private static RenderedQuery render(ScalarSpec<?> scalar, QueryRenderer renderer) {
        return switch (scalar) {
            case CountSpec count -> renderer.render(count);
            case ExistsSpec exists -> renderer.render(exists);
        };
    }

    private static String requireName(String queryName) {
        Objects.requireNonNull(queryName, "queryName cannot be null");
        if (queryName.isBlank()) {
            throw new IllegalArgumentException("queryName cannot be blank");
        }
        return queryName;
    }
}
