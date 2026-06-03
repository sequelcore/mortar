package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a named, rendered row-count mutation.
 *
 * <p>A bound mutation is inspectable SQL plus metadata. It represents
 * insert/update/delete statements whose result is an update count; mutations
 * with {@code RETURNING} columns use {@link MortarReturningMutation} instead.</p>
 */
public record MortarBoundMutation(
    Optional<String> mutationName,
    RenderedQuery rendered,
    MutationResultMode resultMode
) {
    public MortarBoundMutation {
        Objects.requireNonNull(mutationName, "mutationName cannot be null");
        Objects.requireNonNull(rendered, "rendered cannot be null");
        Objects.requireNonNull(resultMode, "resultMode cannot be null");
        mutationName.ifPresent(MortarBoundMutation::requireName);
        if (resultMode != MutationResultMode.ROW_COUNT) {
            throw new IllegalArgumentException("row-count mutation result mode must be ROW_COUNT");
        }
    }

    /**
     * Creates a named row-count mutation from already rendered SQL.
     */
    public static MortarBoundMutation of(String mutationName, RenderedQuery rendered) {
        return new MortarBoundMutation(Optional.of(requireName(mutationName)), rendered, MutationResultMode.ROW_COUNT);
    }

    /**
     * Renders and names a row-count mutation specification.
     */
    public static MortarBoundMutation of(String mutationName, MutationSpec mutation, QueryRenderer renderer) {
        return unnamed(mutation, renderer).named(mutationName);
    }

    /**
     * Creates an unnamed row-count mutation from already rendered SQL.
     */
    public static MortarBoundMutation unnamed(RenderedQuery rendered) {
        return new MortarBoundMutation(Optional.empty(), rendered, MutationResultMode.ROW_COUNT);
    }

    /**
     * Renders an unnamed row-count mutation specification.
     *
     * @throws IllegalArgumentException when the mutation declares returning
     * columns
     */
    public static MortarBoundMutation unnamed(MutationSpec mutation, QueryRenderer renderer) {
        Objects.requireNonNull(mutation, "mutation cannot be null");
        Objects.requireNonNull(renderer, "renderer cannot be null");
        if (!mutation.returning().isEmpty()) {
            throw new IllegalArgumentException("row-count mutations cannot declare returning columns");
        }
        return new MortarBoundMutation(Optional.empty(), render(mutation, renderer), MutationResultMode.ROW_COUNT);
    }

    /**
     * Returns a copy with an inspection name.
     */
    public MortarBoundMutation named(String mutationName) {
        return MortarBoundMutation.of(mutationName, rendered);
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

    static RenderedQuery render(MutationSpec mutation, QueryRenderer renderer) {
        return switch (mutation) {
            case InsertSpec insert -> renderer.render(insert);
            case UpdateSpec update -> renderer.render(update);
            case DeleteSpec delete -> renderer.render(delete);
        };
    }

    private static String requireName(String mutationName) {
        Objects.requireNonNull(mutationName, "mutationName cannot be null");
        if (mutationName.isBlank()) {
            throw new IllegalArgumentException("mutationName cannot be blank");
        }
        return mutationName;
    }
}
