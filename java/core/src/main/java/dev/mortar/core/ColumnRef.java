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

    public Predicate eq(T value) {
        return Predicate.binary(this, Operator.EQUALS, value);
    }

    public Predicate ne(T value) {
        return Predicate.binary(this, Operator.NOT_EQUALS, value);
    }

    public Predicate gt(T value) {
        return Predicate.binary(this, Operator.GREATER_THAN, value);
    }

    public Predicate gte(T value) {
        return Predicate.binary(this, Operator.GREATER_THAN_OR_EQUALS, value);
    }

    public Predicate lt(T value) {
        return Predicate.binary(this, Operator.LESS_THAN, value);
    }

    public Predicate lte(T value) {
        return Predicate.binary(this, Operator.LESS_THAN_OR_EQUALS, value);
    }

    public Predicate containsIgnoreCase(String value) {
        return contains(value, StringComparison.caseInsensitive());
    }

    public Predicate contains(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.CONTAINS, value, comparison);
    }

    public Predicate startsWith(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.STARTS_WITH, value, comparison);
    }

    public Predicate endsWith(String value, StringComparison comparison) {
        ensureStringColumn();
        return Predicate.string(this, StringOperator.ENDS_WITH, value, comparison);
    }

    public Predicate isNull() {
        return Predicate.unary(this, Operator.IS_NULL);
    }

    public Predicate isNotNull() {
        return Predicate.unary(this, Operator.IS_NOT_NULL);
    }

    public Predicate between(T lowerBound, T upperBound) {
        return Predicate.between(this, lowerBound, upperBound);
    }

    public Predicate in(List<T> values) {
        return Predicate.in(this, values);
    }

    public Sort asc() {
        return new Sort(this, SortDirection.ASC);
    }

    public Sort desc() {
        return new Sort(this, SortDirection.DESC);
    }

    private void ensureStringColumn() {
        if (!javaType.equals(String.class)) {
            throw new IllegalStateException("String predicates require a String column");
        }
    }
}
