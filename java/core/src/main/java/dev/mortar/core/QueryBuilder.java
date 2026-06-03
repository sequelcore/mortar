package dev.mortar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Fluent builder for immutable select query specifications.
 */
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

    /**
     * Selects explicit columns for the query.
     *
     * @param first first column to select
     * @param rest additional columns to select
     * @return this builder
     */
    public QueryBuilder<T> select(ColumnRef<?> first, ColumnRef<?>... rest) {
        selectColumns.add(first);
        selectColumns.addAll(List.of(rest));
        return this;
    }

    /**
     * Selects columns through generated table-model accessors.
     *
     * @throws IllegalStateException when this builder was created from a plain
     * table reference instead of a Mortar table model
     */
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

    /**
     * Replaces the current select list with a projection.
     *
     * <p>Projection metadata is later used by runtime adapters that map JDBC
     * rows into records or DTOs.</p>
     */
    public QueryBuilder<T> project(Projection value) {
        projection = value;
        selectColumns.clear();
        selectColumns.addAll(value.allColumns());
        return this;
    }

    /**
     * Builds a projection through a generated table-model accessor.
     *
     * @throws IllegalStateException when this builder was created from a plain
     * table reference instead of a Mortar table model
     */
    public QueryBuilder<T> project(Function<T, Projection> factory) {
        requireModel();
        return project(factory.apply(model));
    }

    /**
     * Projects selected columns into a Java record using its canonical
     * constructor.
     */
    @SafeVarargs
    public final <R> QueryBuilder<T> projectRecord(
        Class<R> targetType,
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        return project(Projection.record(targetType, projectedColumns(first, rest)));
    }

    /**
     * Projects selected columns into a DTO constructor.
     */
    @SafeVarargs
    public final <R> QueryBuilder<T> projectDto(
        Class<R> targetType,
        Function<T, ColumnRef<?>> first,
        Function<T, ColumnRef<?>>... rest
    ) {
        return project(Projection.dto(targetType, projectedColumns(first, rest)));
    }

    /**
     * Adds a predicate to the query.
     */
    public QueryBuilder<T> where(Predicate predicate) {
        predicates.add(predicate);
        return this;
    }

    /**
     * Adds a predicate through a generated table-model accessor.
     *
     * @throws IllegalStateException when this builder was created from a plain
     * table reference instead of a Mortar table model
     */
    public QueryBuilder<T> where(Function<T, Predicate> factory) {
        requireModel();
        return where(factory.apply(model));
    }

    /**
     * Adds a caller-supplied SQL fragment to the predicate list.
     *
     * <p>This is intentionally marked unsafe because Mortar does not parse,
     * validate, or escape the SQL fragment. Parameters should be supplied
     * separately so runtime adapters can still bind them.</p>
     */
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

    /**
     * Applies a positive row limit.
     *
     * @throws IllegalArgumentException when {@code value} is less than one
     */
    public QueryBuilder<T> limit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        limit = value;
        return this;
    }

    /**
     * Applies a zero-based row offset.
     *
     * @throws IllegalArgumentException when {@code value} is negative
     */
    public QueryBuilder<T> offset(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        offset = value;
        return this;
    }

    /**
     * Applies page size and offset from a Mortar page value.
     */
    public QueryBuilder<T> page(MortarPage page) {
        Objects.requireNonNull(page, "page cannot be null");
        limit = page.size();
        offset = page.offset();
        return this;
    }

    /**
     * Assigns an optional inspection name to the query specification.
     *
     * <p>Names are used by testkit assertions, snapshots, diagnostics, and
     * editor tooling. They do not affect rendered SQL.</p>
     */
    public QueryBuilder<T> named(String value) {
        Objects.requireNonNull(value, "name cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        name = value;
        return this;
    }

    /**
     * Builds an immutable query specification without rendering or executing
     * SQL.
     */
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

    /**
     * Builds a scalar {@code count(*)} specification.
     *
     * @throws IllegalStateException when the current builder already has
     * select, projection, sort, limit, or offset state
     */
    public CountSpec count() {
        requireScalarShape();
        return new CountSpec(table, joins, predicates);
    }

    /**
     * Renders a scalar {@code count(*)} query through the supplied renderer and
     * returns an inspectable bound value.
     */
    public MortarBoundScalar<Long> count(QueryRenderer renderer) {
        return MortarBoundScalar.unnamed(count(), renderer);
    }

    /**
     * Builds a scalar {@code exists} specification.
     *
     * @throws IllegalStateException when the current builder already has
     * select, projection, sort, limit, or offset state
     */
    public ExistsSpec exists() {
        requireScalarShape();
        return new ExistsSpec(table, joins, predicates);
    }

    /**
     * Renders a scalar {@code exists} query through the supplied renderer and
     * returns an inspectable bound value.
     */
    public MortarBoundScalar<Boolean> exists(QueryRenderer renderer) {
        return MortarBoundScalar.unnamed(exists(), renderer);
    }

    private void requireModel() {
        if (model == null) {
            throw new IllegalStateException("Lambda-based query operations require a MortarTable model");
        }
    }

    private void requireScalarShape() {
        if (!selectColumns.isEmpty() || projection != null || !sorts.isEmpty() || limit != null || offset != null) {
            throw new IllegalStateException("scalar queries cannot select columns, project rows, sort, limit, or offset");
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
