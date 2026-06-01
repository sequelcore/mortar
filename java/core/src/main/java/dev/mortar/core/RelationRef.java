package dev.mortar.core;

import java.util.Objects;

public record RelationRef(
    String propertyName,
    TableRef targetTable,
    ColumnRef<?> localColumn,
    ColumnRef<?> targetColumn,
    boolean nullable
) {
    public RelationRef(
        String propertyName,
        TableRef targetTable,
        ColumnRef<?> localColumn,
        ColumnRef<?> targetColumn
    ) {
        this(propertyName, targetTable, localColumn, targetColumn, false);
    }

    public RelationRef {
        Objects.requireNonNull(propertyName, "propertyName cannot be null");
        Objects.requireNonNull(targetTable, "targetTable cannot be null");
        Objects.requireNonNull(localColumn, "localColumn cannot be null");
        Objects.requireNonNull(targetColumn, "targetColumn cannot be null");
        if (propertyName.isBlank()) {
            throw new IllegalArgumentException("propertyName cannot be blank");
        }
    }

    public Join innerJoin() {
        return new Join(JoinType.INNER, targetTable, localColumn, targetColumn, nullable);
    }

    public Join leftJoin() {
        return new Join(JoinType.LEFT, targetTable, localColumn, targetColumn, nullable);
    }
}
