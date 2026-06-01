package dev.mortar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tables, columns, and joins touched by a query or mutation.
 */
public record QueryMetadata(List<TableRef> tables, List<ColumnRef<?>> columns, List<Join> joins) {
    public QueryMetadata {
        Objects.requireNonNull(tables, "tables cannot be null");
        Objects.requireNonNull(columns, "columns cannot be null");
        Objects.requireNonNull(joins, "joins cannot be null");
        tables = List.copyOf(tables);
        columns = List.copyOf(columns);
        joins = List.copyOf(joins);
    }

    public static QueryMetadata empty() {
        return new QueryMetadata(List.of(), List.of(), List.of());
    }

    public static QueryMetadata from(QuerySpec query) {
        Objects.requireNonNull(query, "query cannot be null");

        List<TableRef> tables = new ArrayList<>();
        List<ColumnRef<?>> columns = new ArrayList<>();
        addDistinct(tables, query.table());
        query.selectColumns().forEach(column -> addDistinct(columns, column));

        for (Join join : query.joins()) {
            addDistinct(tables, join.table());
            addDistinct(columns, join.leftColumn());
            addDistinct(columns, join.rightColumn());
        }

        query.predicates().forEach(predicate -> addPredicateColumns(columns, predicate));
        query.sorts().forEach(sort -> addDistinct(columns, sort.column()));

        return new QueryMetadata(tables, columns, query.joins());
    }

    public static QueryMetadata from(MutationSpec mutation) {
        Objects.requireNonNull(mutation, "mutation cannot be null");

        List<TableRef> tables = new ArrayList<>();
        List<ColumnRef<?>> columns = new ArrayList<>();
        addDistinct(tables, mutation.table());

        switch (mutation) {
            case InsertSpec insert -> {
                insert.assignments().forEach(assignment -> addDistinct(columns, assignment.column()));
                insert.returning().forEach(column -> addDistinct(columns, column));
            }
            case UpdateSpec update -> {
                update.assignments().forEach(assignment -> addDistinct(columns, assignment.column()));
                update.predicates().forEach(predicate -> addPredicateColumns(columns, predicate));
                update.returning().forEach(column -> addDistinct(columns, column));
            }
            case DeleteSpec delete -> {
                delete.predicates().forEach(predicate -> addPredicateColumns(columns, predicate));
                delete.returning().forEach(column -> addDistinct(columns, column));
            }
        }

        return new QueryMetadata(tables, columns, List.of());
    }

    private static void addPredicateColumns(List<ColumnRef<?>> columns, Predicate predicate) {
        switch (predicate) {
            case Predicate.BetweenPredicate between -> addDistinct(columns, between.column());
            case Predicate.BinaryPredicate binary -> addDistinct(columns, binary.column());
            case Predicate.CompositePredicate composite -> composite.predicates()
                .forEach(child -> addPredicateColumns(columns, child));
            case Predicate.DialectPredicate dialect -> addDistinct(columns, dialect.column());
            case Predicate.InPredicate in -> addDistinct(columns, in.column());
            case Predicate.RawSqlPredicate rawSql -> {
            }
            case Predicate.StringPredicate string -> addDistinct(columns, string.column());
            case Predicate.UnaryPredicate unary -> addDistinct(columns, unary.column());
        }
    }

    private static <T> void addDistinct(List<T> values, T value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
