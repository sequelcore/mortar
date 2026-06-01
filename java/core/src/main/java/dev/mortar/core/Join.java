package dev.mortar.core;

import java.util.Objects;

public record Join(
    JoinType type,
    TableRef table,
    ColumnRef<?> leftColumn,
    ColumnRef<?> rightColumn,
    boolean nullableRelationship
) {
    public Join(JoinType type, TableRef table, ColumnRef<?> leftColumn, ColumnRef<?> rightColumn) {
        this(type, table, leftColumn, rightColumn, false);
    }

    public Join {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(leftColumn, "leftColumn cannot be null");
        Objects.requireNonNull(rightColumn, "rightColumn cannot be null");
    }
}
