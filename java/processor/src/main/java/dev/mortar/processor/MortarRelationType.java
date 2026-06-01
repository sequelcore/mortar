package dev.mortar.processor;

/**
 * Relationship cardinality metadata for generated relation paths.
 */
public enum MortarRelationType {
    /**
     * Many source rows reference one target row.
     */
    MANY_TO_ONE,
    /**
     * One source row references one target row.
     */
    ONE_TO_ONE,
    /**
     * One source row is associated with many target rows.
     */
    ONE_TO_MANY
}
