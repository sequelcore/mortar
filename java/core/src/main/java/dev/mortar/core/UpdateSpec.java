package dev.mortar.core;

import java.util.List;
import java.util.Objects;

public record UpdateSpec(
    TableRef table,
    List<Assignment<?>> assignments,
    List<Predicate> predicates,
    List<ColumnRef<?>> returning
) implements MutationSpec {
    public UpdateSpec {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(assignments, "assignments cannot be null");
        Objects.requireNonNull(predicates, "predicates cannot be null");
        Objects.requireNonNull(returning, "returning cannot be null");
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("assignments cannot be empty");
        }
        assignments.forEach(assignment -> MutationValidation.requireAssignmentTable(table, assignment));
        predicates.forEach(predicate -> MutationValidation.requirePredicateTable(table, predicate));
        returning.forEach(column -> MutationValidation.requireColumnTable(table, column));
        assignments = List.copyOf(assignments);
        predicates = List.copyOf(predicates);
        returning = List.copyOf(returning);
    }
}
