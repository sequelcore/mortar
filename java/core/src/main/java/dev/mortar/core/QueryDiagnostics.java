package dev.mortar.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Static analysis helpers for query specifications and rendered SQL batches.
 */
public final class QueryDiagnostics {
    private static final int DEFAULT_MAX_IN_LIST_VALUES = 100;
    private static final int DEFAULT_REPEATED_QUERY_WARNING_THRESHOLD = 10;

    private QueryDiagnostics() {
    }

    public static List<MortarDiagnostic> analyze(QuerySpec query) {
        Objects.requireNonNull(query, "query cannot be null");

        List<MortarDiagnostic> diagnostics = new ArrayList<>();
        if (query.limit() == null) {
            diagnostics.add(MortarDiagnostic.warning(
                MortarDiagnosticCode.UNBOUNDED_QUERY,
                "Collection query has no limit; add limit or page before execution"
            ));
        }
        if (query.selectColumns().isEmpty() && query.projection().isEmpty()) {
            diagnostics.add(MortarDiagnostic.warning(
                MortarDiagnosticCode.SELECT_ALL,
                "Query uses default select-all projection; select explicit columns outside approved cases"
            ));
        }
        if ((query.limit() != null || query.offset() != null) && query.sorts().isEmpty()) {
            diagnostics.add(MortarDiagnostic.warning(
                MortarDiagnosticCode.UNSTABLE_PAGINATION,
                "Paginated query has no stable ordering; add orderBy before limit or offset"
            ));
        }
        for (Join join : query.joins()) {
            if (join.type() == JoinType.INNER && join.nullableRelationship()) {
                diagnostics.add(MortarDiagnostic.warning(
                    MortarDiagnosticCode.NULLABLE_RELATION_INNER_JOIN,
                    "Nullable relationship uses an inner join; prefer leftJoin or make the relationship non-nullable"
                ));
            }
        }
        query.predicates().forEach(predicate -> addPredicateDiagnostics(predicate, diagnostics));
        addIndexAdvisory(query, diagnostics);
        return List.copyOf(diagnostics);
    }

    public static List<MortarDiagnostic> analyzeRenderedQueries(List<RenderedQuery> renderedQueries) {
        Objects.requireNonNull(renderedQueries, "renderedQueries cannot be null");

        List<MortarDiagnostic> diagnostics = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (RenderedQuery renderedQuery : renderedQueries) {
            Objects.requireNonNull(renderedQuery, "renderedQueries cannot contain null");
            int count = counts.merge(renderedQuery.sql(), 1, Integer::sum);
            if (count == DEFAULT_REPEATED_QUERY_WARNING_THRESHOLD + 1) {
                diagnostics.add(MortarDiagnostic.warning(
                    MortarDiagnosticCode.REPEATED_QUERY_PATTERN,
                    "Rendered SQL pattern repeated more than 10 times; inspect for potential N+1 query behavior"
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void addPredicateDiagnostics(Predicate predicate, List<MortarDiagnostic> diagnostics) {
        switch (predicate) {
            case Predicate.BetweenPredicate between -> {
            }
            case Predicate.BinaryPredicate binary -> {
            }
            case Predicate.CompositePredicate composite -> composite.predicates()
                .forEach(child -> addPredicateDiagnostics(child, diagnostics));
            case Predicate.DialectPredicate dialect -> {
            }
            case Predicate.InPredicate in -> {
                if (in.values().size() > DEFAULT_MAX_IN_LIST_VALUES) {
                    diagnostics.add(MortarDiagnostic.warning(
                        MortarDiagnosticCode.LARGE_IN_LIST,
                        "IN predicate has more than 100 values; prefer a temporary table, join, or bounded input"
                    ));
                }
            }
            case Predicate.RawSqlPredicate rawSql -> {
            }
            case Predicate.StringPredicate string -> {
            }
            case Predicate.UnaryPredicate unary -> {
            }
        }
    }

    private static void addIndexAdvisory(QuerySpec query, List<MortarDiagnostic> diagnostics) {
        List<ColumnRef<?>> indexCandidates = new ArrayList<>();
        for (Join join : query.joins()) {
            addDistinct(indexCandidates, join.leftColumn());
            addDistinct(indexCandidates, join.rightColumn());
        }
        query.predicates().forEach(predicate -> addPredicateIndexCandidates(predicate, indexCandidates));
        query.sorts().forEach(sort -> addDistinct(indexCandidates, sort.column()));

        if (!indexCandidates.isEmpty()) {
            String columns = String.join(", ", indexCandidates.stream()
                .map(QueryDiagnostics::formatColumn)
                .toList());
            diagnostics.add(MortarDiagnostic.info(
                MortarDiagnosticCode.INDEX_ADVISORY,
                "Consider indexes for filtered, joined, or ordered columns: " + columns
            ));
        }
    }

    private static void addPredicateIndexCandidates(Predicate predicate, List<ColumnRef<?>> indexCandidates) {
        switch (predicate) {
            case Predicate.BetweenPredicate between -> addDistinct(indexCandidates, between.column());
            case Predicate.BinaryPredicate binary -> addDistinct(indexCandidates, binary.column());
            case Predicate.CompositePredicate composite -> composite.predicates()
                .forEach(child -> addPredicateIndexCandidates(child, indexCandidates));
            case Predicate.DialectPredicate dialect -> addDistinct(indexCandidates, dialect.column());
            case Predicate.InPredicate in -> addDistinct(indexCandidates, in.column());
            case Predicate.RawSqlPredicate rawSql -> {
            }
            case Predicate.StringPredicate string -> addDistinct(indexCandidates, string.column());
            case Predicate.UnaryPredicate unary -> addDistinct(indexCandidates, unary.column());
        }
    }

    private static void addDistinct(List<ColumnRef<?>> columns, ColumnRef<?> column) {
        if (!columns.contains(column)) {
            columns.add(column);
        }
    }

    private static String formatColumn(ColumnRef<?> column) {
        return column.table().tableName() + "." + column.columnName();
    }
}
