package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a named, rendered row-count mutation.
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

    public static MortarBoundMutation of(String mutationName, RenderedQuery rendered) {
        return new MortarBoundMutation(Optional.of(requireName(mutationName)), rendered, MutationResultMode.ROW_COUNT);
    }

    public static MortarBoundMutation of(String mutationName, MutationSpec mutation, QueryRenderer renderer) {
        return unnamed(mutation, renderer).named(mutationName);
    }

    public static MortarBoundMutation unnamed(RenderedQuery rendered) {
        return new MortarBoundMutation(Optional.empty(), rendered, MutationResultMode.ROW_COUNT);
    }

    public static MortarBoundMutation unnamed(MutationSpec mutation, QueryRenderer renderer) {
        Objects.requireNonNull(mutation, "mutation cannot be null");
        Objects.requireNonNull(renderer, "renderer cannot be null");
        if (!mutation.returning().isEmpty()) {
            throw new IllegalArgumentException("row-count mutations cannot declare returning columns");
        }
        return new MortarBoundMutation(Optional.empty(), render(mutation, renderer), MutationResultMode.ROW_COUNT);
    }

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
