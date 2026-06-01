package dev.mortar.core;

final class MutationValidation {
    private MutationValidation() {
    }

    static void requireAssignmentTable(TableRef table, Assignment<?> assignment) {
        requireColumnTable(table, assignment.column());
    }

    static void requireColumnTable(TableRef table, ColumnRef<?> column) {
        if (column.table().equals(table)) {
            return;
        }

        if (column.table().tableName().equals(table.tableName())) {
            throw new IllegalArgumentException(
                "column "
                    + column.columnName()
                    + " belongs to table "
                    + column.table().tableName()
                    + " as "
                    + column.table().alias()
                    + ", not "
                    + table.tableName()
                    + " as "
                    + table.alias()
            );
        }

        throw new IllegalArgumentException(
            "column "
                + column.columnName()
                + " belongs to table "
                + column.table().tableName()
                + ", not "
                + table.tableName()
        );
    }

    static void requirePredicateTable(TableRef table, Predicate predicate) {
        switch (predicate) {
            case Predicate.BetweenPredicate between -> requireColumnTable(table, between.column());
            case Predicate.BinaryPredicate binary -> requireColumnTable(table, binary.column());
            case Predicate.CompositePredicate composite -> composite.predicates()
                .forEach(child -> requirePredicateTable(table, child));
            case Predicate.DialectPredicate dialect -> requireColumnTable(table, dialect.column());
            case Predicate.InPredicate in -> requireColumnTable(table, in.column());
            case Predicate.RawSqlPredicate rawSql -> {
            }
            case Predicate.StringPredicate string -> requireColumnTable(table, string.column());
            case Predicate.UnaryPredicate unary -> requireColumnTable(table, unary.column());
        }
    }
}
