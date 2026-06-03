package dev.mortar.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable count scalar query model rendered by dialect adapters.
 */
public record CountSpec(
    TableRef table,
    List<Join> joins,
    List<Predicate> predicates
) implements ScalarSpec<Long> {
    public CountSpec {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(joins, "joins cannot be null");
        Objects.requireNonNull(predicates, "predicates cannot be null");
        joins = List.copyOf(joins);
        predicates = List.copyOf(predicates);
    }

    @Override
    public Class<Long> scalarType() {
        return Long.class;
    }
}
