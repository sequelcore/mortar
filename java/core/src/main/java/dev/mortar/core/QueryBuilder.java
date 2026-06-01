package dev.mortar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class QueryBuilder<T> {
    private final TableRef table;
    private final T model;
    private final List<ColumnRef<?>> selectColumns = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final List<Predicate> predicates = new ArrayList<>();
    private final List<Sort> sorts = new ArrayList<>();
    private Projection projection;
    private String name;
    private Integer limit;
    private Integer offset;

    QueryBuilder(TableRef table) {
        this(table, null);
    }

    QueryBuilder(TableRef table, T model) {
        this.table = table;
        this.model = model;
    }

    public QueryBuilder<T> select(ColumnRef<?> first, ColumnRef<?>... rest) {
        selectColumns.add(first);
        selectColumns.addAll(List.of(rest));
        return this;
    }

    @SafeVarargs
    public final QueryBuilder<T> select(
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        requireModel();
        selectColumns.add(first.apply(model));
        for (Function<T, ColumnRef<?>> selector : rest) {
            selectColumns.add(selector.apply(model));
        }
        return this;
    }

    public QueryBuilder<T> project(Projection value) {
        projection = value;
        selectColumns.clear();
        selectColumns.addAll(value.allColumns());
        return this;
    }

    public QueryBuilder<T> project(Function<T, Projection> factory) {
        requireModel();
        return project(factory.apply(model));
    }

    @SafeVarargs
    public final <R> QueryBuilder<T> projectRecord(
        Class<R> targetType,
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        return project(Projection.record(targetType, projectedColumns(first, rest)));
    }

    @SafeVarargs
    public final <R> QueryBuilder<T> projectDto(
        Class<R> targetType,
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        return project(Projection.dto(targetType, projectedColumns(first, rest)));
    }

    public QueryBuilder<T> where(Predicate predicate) {
        predicates.add(predicate);
        return this;
    }

    public QueryBuilder<T> where(Function<T, Predicate> factory) {
        requireModel();
        return where(factory.apply(model));
    }

    public QueryBuilder<T> unsafeWhereRaw(String sql, Parameter... parameters) {
        return where(Predicate.unsafeRaw(sql, List.of(parameters)));
    }

    public QueryBuilder<T> innerJoin(TableRef table, ColumnRef<?> leftColumn, ColumnRef<?> rightColumn) {
        joins.add(new Join(JoinType.INNER, table, leftColumn, rightColumn));
        return this;
    }

    public QueryBuilder<T> innerJoin(Function<T, RelationRef> relation) {
        requireModel();
        joins.add(relation.apply(model).innerJoin());
        return this;
    }

    public QueryBuilder<T> leftJoin(TableRef table, ColumnRef<?> leftColumn, ColumnRef<?> rightColumn) {
        joins.add(new Join(JoinType.LEFT, table, leftColumn, rightColumn));
        return this;
    }

    public QueryBuilder<T> leftJoin(Function<T, RelationRef> relation) {
        requireModel();
        joins.add(relation.apply(model).leftJoin());
        return this;
    }

    public QueryBuilder<T> orderBy(Sort sort) {
        sorts.add(sort);
        return this;
    }

    public QueryBuilder<T> orderBy(Function<T, Sort> factory) {
        requireModel();
        return orderBy(factory.apply(model));
    }

    public QueryBuilder<T> limit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        limit = value;
        return this;
    }

    public QueryBuilder<T> offset(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        offset = value;
        return this;
    }

    public QueryBuilder<T> page(MortarPage page) {
        Objects.requireNonNull(page, "page cannot be null");
        limit = page.size();
        offset = page.offset();
        return this;
    }

    public QueryBuilder<T> named(String value) {
        Objects.requireNonNull(value, "name cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        name = value;
        return this;
    }

    public QuerySpec build() {
        return new QuerySpec(
            java.util.Optional.ofNullable(name),
            table,
            selectColumns,
            java.util.Optional.ofNullable(projection),
            joins,
            predicates,
            sorts,
            limit,
            offset
        );
    }

    private void requireModel() {
        if (model == null) {
            throw new IllegalStateException("Lambda-based query operations require a MortarTable model");
        }
    }

    @SafeVarargs
    private final List<ColumnRef<?>> projectedColumns(
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        requireModel();
        List<ColumnRef<?>> columns = new ArrayList<>();
        columns.add(first.apply(model));
        for (Function<T, ColumnRef<?>> selector : rest) {
            columns.add(selector.apply(model));
        }
        return columns;
    }
}
