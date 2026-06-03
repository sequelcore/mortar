package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free description of a named, rendered mutation that returns rows.
 *
 * <p>This value is for dialects that support mutation result sets, such as
 * PostgreSQL {@code RETURNING}. It is inspectable and still requires an
 * explicit runtime adapter call to execute.</p>
 */
public record MortarReturningMutation<T>(
    Optional<String> mutationName,
    RenderedQuery rendered,
    Class<T> rowType,
    List<ColumnRef<?>> returningColumns,
    MutationResultMode resultMode
) {
    public MortarReturningMutation {
        Objects.requireNonNull(mutationName, "mutationName cannot be null");
        Objects.requireNonNull(rendered, "rendered cannot be null");
        Objects.requireNonNull(rowType, "rowType cannot be null");
        Objects.requireNonNull(returningColumns, "returningColumns cannot be null");
        Objects.requireNonNull(resultMode, "resultMode cannot be null");
        mutationName.ifPresent(MortarReturningMutation::requireName);
        if (resultMode != MutationResultMode.RETURNING_ROWS) {
            throw new IllegalArgumentException("returning mutation result mode must be RETURNING_ROWS");
        }
        if (returningColumns.isEmpty()) {
            throw new IllegalArgumentException("returning mutation columns cannot be empty");
        }
        returningColumns = List.copyOf(returningColumns);
    }

    /**
     * Renders and names a mutation that declares returning columns.
     */
    public static <T> MortarReturningMutation<T> of(
        String mutationName,
        MutationSpec mutation,
        QueryRenderer renderer,
        Class<T> rowType
    ) {
        return unnamed(mutation, renderer, rowType).named(mutationName);
    }

    /**
     * Renders an unnamed mutation that declares returning columns.
     *
     * @throws IllegalArgumentException when the mutation has no returning
     * columns
     */
    public static <T> MortarReturningMutation<T> unnamed(
        MutationSpec mutation,
        QueryRenderer renderer,
        Class<T> rowType
    ) {
        Objects.requireNonNull(mutation, "mutation cannot be null");
        Objects.requireNonNull(renderer, "renderer cannot be null");
        Objects.requireNonNull(rowType, "rowType cannot be null");
        if (mutation.returning().isEmpty()) {
            throw new IllegalArgumentException("returning mutations require at least one returning column");
        }
        return new MortarReturningMutation<>(
            Optional.empty(),
            MortarBoundMutation.render(mutation, renderer),
            rowType,
            mutation.returning(),
            MutationResultMode.RETURNING_ROWS
        );
    }

    /**
     * Returns a copy with an inspection name.
     */
    public MortarReturningMutation<T> named(String mutationName) {
        return new MortarReturningMutation<>(
            Optional.of(requireName(mutationName)),
            rendered,
            rowType,
            returningColumns,
            MutationResultMode.RETURNING_ROWS
        );
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

    private static String requireName(String mutationName) {
        Objects.requireNonNull(mutationName, "mutationName cannot be null");
        if (mutationName.isBlank()) {
            throw new IllegalArgumentException("mutationName cannot be blank");
        }
        return mutationName;
    }
}
