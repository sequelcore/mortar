package dev.mortar.core;

import java.util.Objects;

public record Assignment<T>(ColumnRef<T> column, Parameter value) {
    public Assignment {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static <T> Assignment<T> of(ColumnRef<T> column, T value) {
        Objects.requireNonNull(column, "column cannot be null");
        return new Assignment<>(column, new Parameter(value, column.javaType()));
    }
}
