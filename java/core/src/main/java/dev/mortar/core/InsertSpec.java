package dev.mortar.core;

import java.util.List;
import java.util.Objects;

public record InsertSpec(
    TableRef table,
    List<Assignment<?>> assignments,
    List<ColumnRef<?>> returning
) implements MutationSpec {
    public InsertSpec {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(assignments, "assignments cannot be null");
        Objects.requireNonNull(returning, "returning cannot be null");
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("assignments cannot be empty");
        }
        assignments.forEach(assignment -> MutationValidation.requireAssignmentTable(table, assignment));
        returning.forEach(column -> MutationValidation.requireColumnTable(table, column));
        assignments = List.copyOf(assignments);
        returning = List.copyOf(returning);
    }
}
