package dev.mortar.core;

import java.util.Objects;

public record Sort(ColumnRef<?> column, SortDirection direction) {
    public Sort {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
    }
}
