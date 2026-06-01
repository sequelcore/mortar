package dev.mortar.core;

public interface MortarDb {
    <T> QueryBuilder<T> from(TableRef table);

    <T extends MortarTable> QueryBuilder<T> from(T table);
}
