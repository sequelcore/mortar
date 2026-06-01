package dev.mortar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Projection target and column list for scalar, record, DTO, or nested mapping.
 */
public record Projection(
    ProjectionKind kind,
    Optional<Class<?>> targetType,
    List<ColumnRef<?>> columns,
    List<Projection> children
) {
    public Projection {
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(targetType, "targetType cannot be null");
        Objects.requireNonNull(columns, "columns cannot be null");
        Objects.requireNonNull(children, "children cannot be null");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns cannot be empty");
        }
        if (kind == ProjectionKind.NESTED && children.isEmpty()) {
            throw new IllegalArgumentException("nested projection children cannot be empty");
        }
        columns = List.copyOf(columns);
        children = List.copyOf(children);
    }

    public static Projection scalar(ColumnRef<?> column) {
        return new Projection(ProjectionKind.SCALAR, Optional.empty(), List.of(column), List.of());
    }

    public static Projection record(Class<?> targetType, List<ColumnRef<?>> columns) {
        return new Projection(ProjectionKind.RECORD, Optional.of(targetType), columns, List.of());
    }

    public static Projection dto(Class<?> targetType, List<ColumnRef<?>> columns) {
        return new Projection(ProjectionKind.DTO, Optional.of(targetType), columns, List.of());
    }

    public static Projection nested(Class<?> targetType, List<ColumnRef<?>> columns, List<Projection> children) {
        return new Projection(ProjectionKind.NESTED, Optional.of(targetType), columns, children);
    }

    public List<ColumnRef<?>> allColumns() {
        List<ColumnRef<?>> allColumns = new ArrayList<>(columns);
        children.forEach(child -> allColumns.addAll(child.allColumns()));
        return List.copyOf(allColumns);
    }
}
