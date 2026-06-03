package dev.mortar.core;

import java.util.List;
import java.util.Objects;

/**
 * Typed reference to a table column used for predicates, sorting, and projections.
 */
public record ColumnRef<T>(TableRef table, String propertyName, String columnName, Class<T> javaType) {
    public ColumnRef {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(propertyName, "propertyName cannot be null");
        Objects.requireNonNull(columnName, "columnName cannot be null");
        Objects.requireNonNull(javaType, "javaType cannot be null");

        if (propertyName.isBlank()) {
            throw new IllegalArgumentException("propertyName cannot be blank");
        }
        if (columnName.isBlank()) {
            throw new IllegalArgumentException("columnName cannot be blank");
        }
    }

    /**
     * Builds an equality predicate.
     */
    public Predicate eq(T value) {
        return Predicate.binary(this, Operator.EQUALS, value);
    }

    /**
     * Builds an inequality predicate.
     */
    public Predicate ne(T value) {
        return Predicate.binary(this, Operator.NOT_EQUALS, value);
    }

    /**
     * Builds a greater-than predicate.
     */
    public Predicate gt(T value) {
        return Predicate.binary(this, Operator.GREATER_THAN, value);
    }

    /**
     * Builds a greater-than-or-equal predicate.
     */
    public Predicate gte(T value) {
        return Predicate.binary(this, Operator.GREATER_THAN_OR_EQUALS, value);
    }

    /**
     * Builds a less-than predicate.
     */
    public Predicate lt(T value) {
        return Predicate.binary(this, Operator.LESS_THAN, value);
    }

    /**
     * Builds a less-than-or-equal predicate.
     */
    public Predicate lte(T value) {
        return Predicate.binary(this, Operator.LESS_THAN_OR_EQUALS, value);
    }

    /**
     * Builds a case-insensitive string contains predicate.
     *
     * @throws IllegalStateException when this column is not a String column
     */
    public Predicate containsIgnoreCase(String value) {
        return contains(value, StringComparison.caseInsensitive());
    }

    /**
     * Builds a string contains predicate with the supplied comparison policy.
     *
     * @throws IllegalStateException when this column is not a String column
     */
    public Predicate contains(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.CONTAINS, value, comparison);
    }

    /**
     * Builds a string starts-with predicate with the supplied comparison policy.
     *
     * @throws IllegalStateException when this column is not a String column
     */
    public Predicate startsWith(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.STARTS_WITH, value, comparison);
    }

    /**
     * Builds a string ends-with predicate with the supplied comparison policy.
     *
     * @throws IllegalStateException when this column is not a String column
     */
    public Predicate endsWith(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.ENDS_WITH, value, comparison);
    }

    /**
     * Builds an {@code is null} predicate.
     */
    public Predicate isNull() {
        return Predicate.unary(this, Operator.IS_NULL);
    }

    /**
     * Builds an {@code is not null} predicate.
     */
    public Predicate isNotNull() {
        return Predicate.unary(this, Operator.IS_NOT_NULL);
    }

    /**
     * Builds an inclusive range predicate.
     */
    public Predicate between(T lowerBound, T upperBound) {
        return Predicate.between(this, lowerBound, upperBound);
    }

    /**
     * Builds an {@code in (...)} predicate.
     */
    public Predicate in(List<T> values) {
        return Predicate.in(this, values);
    }

    /**
     * Builds an ascending sort expression.
     */
    public Sort asc() {
        return new Sort(this, SortDirection.ASC);
    }

    /**
     * Builds a descending sort expression.
     */
    public Sort desc() {
        return new Sort(this, SortDirection.DESC);
    }

    private void ensureStringColumn() {
        if (!javaType.equals(String.class)) {
            throw new IllegalStateException("String predicates require a String column");
        }
    }
}
