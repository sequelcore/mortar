package dev.mortar.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable select query model consumed by SQL renderers and diagnostics.
 */
public record QuerySpec(
    Optional<String> name,
    TableRef table,
    List<ColumnRef<?>> selectColumns,
    Optional<Projection> projection,
    List<Join> joins,
    List<Predicate> predicates,
    List<Sort> sorts,
    Integer limit,
    Integer offset
) {
    public QuerySpec {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(selectColumns, "selectColumns cannot be null");
        Objects.requireNonNull(projection, "projection cannot be null");
        Objects.requireNonNull(joins, "joins cannot be null");
        Objects.requireNonNull(predicates, "predicates cannot be null");
        Objects.requireNonNull(sorts, "sorts cannot be null");

        selectColumns = List.copyOf(selectColumns);
        joins = List.copyOf(joins);
        predicates = List.copyOf(predicates);
        sorts = List.copyOf(sorts);
    }
}
