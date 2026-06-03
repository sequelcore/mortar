package dev.mortar.core;

/**
 * Framework-free entry point for building Mortar query specifications.
 */
public interface MortarDb {
    /**
     * Starts a query from an explicit table reference.
     *
     * <p>The returned builder creates an inspectable query model only. Rendering
     * and execution remain separate adapter responsibilities.</p>
     *
     * @param table table metadata for the root relation
     * @return a new query builder
     */
    <T> QueryBuilder<T> from(TableRef table);

    /**
     * Starts a query from a generated or handwritten Mortar table model.
     *
     * <p>This overload enables lambda-based column and relation selectors. It
     * still produces a query specification rather than executing SQL.</p>
     *
     * @param table table model for the root relation
     * @return a new query builder bound to the supplied table model
     */
    <T extends MortarTable> QueryBuilder<T> from(T table);
}
