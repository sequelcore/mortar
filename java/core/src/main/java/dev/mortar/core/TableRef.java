package dev.mortar.core;

import java.util.Objects;

/**
 * SQL table name and alias used by query specs and generated metamodels.
 */
public record TableRef(String tableName, String alias) {
    public TableRef {
        Objects.requireNonNull(tableName, "tableName cannot be null");
        Objects.requireNonNull(alias, "alias cannot be null");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName cannot be blank");
        }
        if (alias.isBlank()) {
            throw new IllegalArgumentException("alias cannot be blank");
        }
    }

    public <T> ColumnRef<T> column(String propertyName, String columnName, Class<T> javaType) {
        return new ColumnRef<>(this, propertyName, columnName, javaType);
    }
}
