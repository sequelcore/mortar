package dev.mortar.postgres;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Parameter;
import dev.mortar.core.Predicate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PostgreSQL-specific predicate factories for arrays, JSONB, and full-text search.
 */
public final class PostgresPredicates {
    static final String DIALECT = "postgres";
    static final String ARRAY_CONTAINS = "array_contains";
    static final String ARRAY_OVERLAPS = "array_overlaps";
    static final String JSONB_CONTAINS = "jsonb_contains";
    static final String FULL_TEXT_WEBSEARCH = "full_text_websearch";
    static final String TEXT_SEARCH_CONFIG = "textSearchConfig";

    private PostgresPredicates() {
    }

    /**
     * Builds a PostgreSQL array containment predicate using {@code @>}.
     *
     * @throws IllegalArgumentException when the column is not an array column or
     * values are empty
     */
    public static Predicate arrayContains(ColumnRef<?> column, List<?> values) {
        return arrayPredicate(ARRAY_CONTAINS, column, values);
    }

    /**
     * Builds a PostgreSQL array overlap predicate using {@code &&}.
     *
     * @throws IllegalArgumentException when the column is not an array column or
     * values are empty
     */
    public static Predicate arrayOverlaps(ColumnRef<?> column, List<?> values) {
        return arrayPredicate(ARRAY_OVERLAPS, column, values);
    }

    /**
     * Builds a PostgreSQL JSONB containment predicate using {@code @>}.
     *
     * @throws IllegalArgumentException when {@code json} is blank
     */
    public static Predicate jsonbContains(ColumnRef<?> column, String json) {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(json, "json cannot be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("json cannot be blank");
        }
        return Predicate.dialect(DIALECT, JSONB_CONTAINS, column, List.of(Parameter.of(json)), Map.of());
    }

    /**
     * Builds a PostgreSQL full-text search predicate using
     * {@code websearch_to_tsquery}.
     *
     * @throws IllegalStateException when the column is not a String column
     * @throws IllegalArgumentException when the text-search configuration is not
     * a valid identifier or the query is blank
     */
    public static Predicate webSearch(ColumnRef<String> column, String config, String query) {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(query, "query cannot be null");
        if (!String.class.equals(column.javaType())) {
            throw new IllegalStateException("Full-text predicates require a String column");
        }
        if (!config.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL text search configuration: " + config);
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException("query cannot be blank");
        }
        return Predicate.dialect(
            DIALECT,
            FULL_TEXT_WEBSEARCH,
            column,
            List.of(Parameter.of(query)),
            Map.of(TEXT_SEARCH_CONFIG, config)
        );
    }

    private static Predicate arrayPredicate(String operator, ColumnRef<?> column, List<?> values) {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(values, "values cannot be null");
        if (!column.javaType().isArray()) {
            throw new IllegalArgumentException("PostgreSQL array predicates require an array column: " + column.propertyName());
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values cannot be empty");
        }
        return Predicate.dialect(DIALECT, operator, column, values.stream().map(Parameter::of).toList(), Map.of());
    }
}
