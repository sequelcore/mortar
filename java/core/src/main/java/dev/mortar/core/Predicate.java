package dev.mortar.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface Predicate permits
    Predicate.BetweenPredicate,
    Predicate.BinaryPredicate,
    Predicate.CompositePredicate,
    Predicate.DialectPredicate,
    Predicate.InPredicate,
    Predicate.RawSqlPredicate,
    Predicate.StringPredicate,
    Predicate.UnaryPredicate {
    static Predicate binary(ColumnRef<?> column, Operator operator, Object value) {
        return new BinaryPredicate(column, operator, Parameter.of(value));
    }

    static Predicate unary(ColumnRef<?> column, Operator operator) {
        return new UnaryPredicate(column, operator);
    }

    static Predicate between(ColumnRef<?> column, Object lowerBound, Object upperBound) {
        return new BetweenPredicate(column, Parameter.of(lowerBound), Parameter.of(upperBound));
    }

    static Predicate in(ColumnRef<?> column, List<?> values) {
        return new InPredicate(column, values.stream().map(Parameter::of).toList());
    }

    static Predicate string(
        ColumnRef<?> column,
        StringOperator operator,
        String value,
        StringComparison comparison
    ) {
        return new StringPredicate(column, operator, value, comparison);
    }

    static Predicate dialect(
        String dialect,
        String operator,
        ColumnRef<?> column,
        List<Parameter> parameters,
        Map<String, String> options
    ) {
        return new DialectPredicate(dialect, operator, column, parameters, options);
    }

    static Predicate unsafeRaw(String sql, List<Parameter> parameters) {
        return new RawSqlPredicate(sql, parameters);
    }

    static Predicate and(List<Predicate> predicates) {
        return new CompositePredicate(CompositeOperator.AND, predicates);
    }

    static Predicate or(List<Predicate> predicates) {
        return new CompositePredicate(CompositeOperator.OR, predicates);
    }

    enum CompositeOperator {
        AND,
        OR
    }

    record UnaryPredicate(ColumnRef<?> column, Operator operator) implements Predicate {
        public UnaryPredicate {
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(operator, "operator cannot be null");
        }
    }

    record BinaryPredicate(ColumnRef<?> column, Operator operator, Parameter parameter) implements Predicate {
        public BinaryPredicate {
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(operator, "operator cannot be null");
            Objects.requireNonNull(parameter, "parameter cannot be null");
        }
    }

    record BetweenPredicate(ColumnRef<?> column, Parameter lowerBound, Parameter upperBound) implements Predicate {
        public BetweenPredicate {
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(lowerBound, "lowerBound cannot be null");
            Objects.requireNonNull(upperBound, "upperBound cannot be null");
        }
    }

    record InPredicate(ColumnRef<?> column, List<Parameter> values) implements Predicate {
        public InPredicate {
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(values, "values cannot be null");
            if (values.isEmpty()) {
                throw new IllegalArgumentException("values cannot be empty");
            }
            values = List.copyOf(values);
        }
    }

    record StringPredicate(
        ColumnRef<?> column,
        StringOperator operator,
        String value,
        StringComparison comparison
    ) implements Predicate {
        public StringPredicate {
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(operator, "operator cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            Objects.requireNonNull(comparison, "comparison cannot be null");
        }
    }

    record DialectPredicate(
        String dialect,
        String operator,
        ColumnRef<?> column,
        List<Parameter> parameters,
        Map<String, String> options
    ) implements Predicate {
        public DialectPredicate {
            Objects.requireNonNull(dialect, "dialect cannot be null");
            Objects.requireNonNull(operator, "operator cannot be null");
            Objects.requireNonNull(column, "column cannot be null");
            Objects.requireNonNull(parameters, "parameters cannot be null");
            Objects.requireNonNull(options, "options cannot be null");
            if (dialect.isBlank()) {
                throw new IllegalArgumentException("dialect cannot be blank");
            }
            if (operator.isBlank()) {
                throw new IllegalArgumentException("operator cannot be blank");
            }
            parameters = List.copyOf(parameters);
            options = Map.copyOf(options);
        }
    }

    record RawSqlPredicate(String sql, List<Parameter> parameters) implements Predicate {
        public RawSqlPredicate {
            Objects.requireNonNull(sql, "sql cannot be null");
            Objects.requireNonNull(parameters, "parameters cannot be null");
            if (sql.isBlank()) {
                throw new IllegalArgumentException("sql cannot be blank");
            }
            parameters = List.copyOf(parameters);
        }
    }

    record CompositePredicate(CompositeOperator operator, List<Predicate> predicates) implements Predicate {
        public CompositePredicate {
            Objects.requireNonNull(operator, "operator cannot be null");
            Objects.requireNonNull(predicates, "predicates cannot be null");
            if (predicates.isEmpty()) {
                throw new IllegalArgumentException("predicates cannot be empty");
            }
            predicates = List.copyOf(predicates);
        }
    }
}
