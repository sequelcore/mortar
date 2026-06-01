package dev.mortar.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable delete mutation model rendered by dialect adapters.
 */
public record DeleteSpec(
    TableRef table,
    List<Predicate> predicates,
    List<ColumnRef<?>> returning
) implements MutationSpec {
    public DeleteSpec {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(predicates, "predicates cannot be null");
        Objects.requireNonNull(returning, "returning cannot be null");
        predicates.forEach(predicate -> MutationValidation.requirePredicateTable(table, predicate));
        returning.forEach(column -> MutationValidation.requireColumnTable(table, column));
        predicates = List.copyOf(predicates);
        returning = List.copyOf(returning);
    }
}
