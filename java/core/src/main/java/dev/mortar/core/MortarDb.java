package dev.mortar.core;

/**
 * Framework-free entry point for building Mortar query specifications.
 */
public interface MortarDb {
    <T> QueryBuilder<T> from(TableRef table);

    <T extends MortarTable> QueryBuilder<T> from(T table);
}
