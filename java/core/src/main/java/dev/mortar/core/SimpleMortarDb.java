package dev.mortar.core;

import java.util.Objects;

public final class SimpleMortarDb implements MortarDb {
    @Override
    public <T> QueryBuilder<T> from(TableRef table) {
        Objects.requireNonNull(table, "table cannot be null");
        return new QueryBuilder<>(table);
    }

    @Override
    public <T extends MortarTable> QueryBuilder<T> from(T table) {
        Objects.requireNonNull(table, "table cannot be null");
        return new QueryBuilder<>(table.table(), table);
    }
}
