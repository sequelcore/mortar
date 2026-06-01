package dev.mortar.postgres;

import dev.mortar.core.Assignment;
import dev.mortar.core.ColumnRef;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.Join;
import dev.mortar.core.JoinType;
import dev.mortar.core.Operator;
import dev.mortar.core.Parameter;
import dev.mortar.core.Predicate;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.Sort;
import dev.mortar.core.StringCaseStrategy;
import dev.mortar.core.StringOperator;
import dev.mortar.core.UpdateSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PostgreSQL SQL renderer for Mortar query and mutation specifications.
 */
public final class PostgresQueryRenderer implements QueryRenderer {
    private final PostgresSqlFormat sqlFormat;

    public PostgresQueryRenderer() {
        this(PostgresSqlFormat.COMPACT);
    }

    public PostgresQueryRenderer(PostgresSqlFormat sqlFormat) {
        this.sqlFormat = Objects.requireNonNull(sqlFormat, "sqlFormat cannot be null");
    }

    @Override
    public RenderedQuery render(QuerySpec query) {
        List<Parameter> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(128);

        sql.append("select ");
        sql.append(renderSelect(query));
        appendClause(sql, "from ");
        sql.append(identifier(query.table().tableName()));
        sql.append(" ");
        sql.append(identifier(query.table().alias()));

        for (Join join : query.joins()) {
            appendClause(sql, renderJoin(join));
        }

        if (!query.predicates().isEmpty()) {
            appendClause(sql, "where ");
            sql.append(query.predicates().stream()
                .map(predicate -> renderPredicate(predicate, parameters))
                .collect(Collectors.joining(" and ")));
        }

        if (!query.sorts().isEmpty()) {
            appendClause(sql, "order by ");
            sql.append(query.sorts().stream()
                .map(this::renderSort)
                .collect(Collectors.joining(", ")));
        }

        if (query.limit() != null) {
            appendClause(sql, "limit ?");
            parameters.add(Parameter.of(query.limit()));
        }

        if (query.offset() != null) {
            appendClause(sql, "offset ?");
            parameters.add(Parameter.of(query.offset()));
        }

        return new RenderedQuery(sql.toString(), parameters, QueryMetadata.from(query));
    }

    @Override
    public RenderedQuery render(InsertSpec insert) {
        List<Parameter> parameters = new ArrayList<>();
        String columns = insert.assignments().stream()
            .map(Assignment::column)
            .map(column -> renderColumn(column, ColumnRenderMode.BARE))
            .collect(Collectors.joining(", "));
        String placeholders = insert.assignments().stream()
            .map(assignment -> "?")
            .collect(Collectors.joining(", "));
        insert.assignments().forEach(assignment -> parameters.add(assignment.value()));

        StringBuilder sql = new StringBuilder(128);
        sql.append("insert into ");
        sql.append(identifier(insert.table().tableName()));
        sql.append(" (");
        sql.append(columns);
        sql.append(")");
        appendClause(sql, "values (");
        sql.append(placeholders);
        sql.append(")");
        appendReturning(sql, insert.returning());

        return new RenderedQuery(sql.toString(), parameters, QueryMetadata.from(insert));
    }

    @Override
    public RenderedQuery render(UpdateSpec update) {
        List<Parameter> parameters = new ArrayList<>();
        String assignments = update.assignments().stream()
            .map(assignment -> renderAssignment(assignment, parameters))
            .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder(128);
        sql.append("update ");
        sql.append(identifier(update.table().tableName()));
        appendClause(sql, "set ");
        sql.append(assignments);

        if (!update.predicates().isEmpty()) {
            appendClause(sql, "where ");
            sql.append(update.predicates().stream()
                .map(predicate -> renderPredicate(predicate, parameters, ColumnRenderMode.BARE))
                .collect(Collectors.joining(" and ")));
        }

        appendReturning(sql, update.returning());

        return new RenderedQuery(sql.toString(), parameters, QueryMetadata.from(update));
    }

    @Override
    public RenderedQuery render(DeleteSpec delete) {
        List<Parameter> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(128);
        sql.append("delete from ");
        sql.append(identifier(delete.table().tableName()));

        if (!delete.predicates().isEmpty()) {
            appendClause(sql, "where ");
            sql.append(delete.predicates().stream()
                .map(predicate -> renderPredicate(predicate, parameters, ColumnRenderMode.BARE))
                .collect(Collectors.joining(" and ")));
        }

        appendReturning(sql, delete.returning());

        return new RenderedQuery(sql.toString(), parameters, QueryMetadata.from(delete));
    }

    private String renderSelect(QuerySpec query) {
        if (query.selectColumns().isEmpty()) {
            return identifier(query.table().alias()) + ".*";
        }

        return query.selectColumns().stream()
            .map(this::renderColumn)
            .collect(Collectors.joining(", "));
    }

    private String renderPredicate(Predicate predicate, List<Parameter> parameters) {
        return renderPredicate(predicate, parameters, ColumnRenderMode.ALIASED);
    }

    private String renderPredicate(Predicate predicate, List<Parameter> parameters, ColumnRenderMode columnRenderMode) {
        return switch (predicate) {
            case Predicate.BetweenPredicate between -> renderBetweenPredicate(between, parameters, columnRenderMode);
            case Predicate.BinaryPredicate binary -> renderBinaryPredicate(binary, parameters, columnRenderMode);
            case Predicate.CompositePredicate composite -> composite.predicates().stream()
                .map(child -> renderPredicate(child, parameters, columnRenderMode))
                .collect(Collectors.joining(" " + composite.operator().name().toLowerCase(Locale.ROOT) + " ", "(", ")"));
            case Predicate.DialectPredicate dialect -> renderDialectPredicate(dialect, parameters, columnRenderMode);
            case Predicate.InPredicate in -> renderInPredicate(in, parameters, columnRenderMode);
            case Predicate.RawSqlPredicate rawSql -> renderRawSqlPredicate(rawSql, parameters);
            case Predicate.StringPredicate string -> renderStringPredicate(string, parameters, columnRenderMode);
            case Predicate.UnaryPredicate unary -> renderUnaryPredicate(unary, columnRenderMode);
        };
    }

    private String renderBinaryPredicate(
        Predicate.BinaryPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        ColumnRef<?> column = predicate.column();
        Operator operator = predicate.operator();

        if (operator == Operator.EQUALS) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " = ?";
        }

        if (operator == Operator.NOT_EQUALS) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " <> ?";
        }

        if (operator == Operator.GREATER_THAN) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " > ?";
        }

        if (operator == Operator.GREATER_THAN_OR_EQUALS) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " >= ?";
        }

        if (operator == Operator.LESS_THAN) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " < ?";
        }

        if (operator == Operator.LESS_THAN_OR_EQUALS) {
            parameters.add(predicate.parameter());
            return renderColumn(column, columnRenderMode) + " <= ?";
        }

        throw new IllegalArgumentException("Unsupported operator: " + operator);
    }

    private String renderUnaryPredicate(Predicate.UnaryPredicate predicate, ColumnRenderMode columnRenderMode) {
        if (predicate.operator() == Operator.IS_NULL) {
            return renderColumn(predicate.column(), columnRenderMode) + " is null";
        }

        if (predicate.operator() == Operator.IS_NOT_NULL) {
            return renderColumn(predicate.column(), columnRenderMode) + " is not null";
        }

        throw new IllegalArgumentException("Unsupported unary operator: " + predicate.operator());
    }

    private String renderBetweenPredicate(
        Predicate.BetweenPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        parameters.add(predicate.lowerBound());
        parameters.add(predicate.upperBound());
        return renderColumn(predicate.column(), columnRenderMode) + " between ? and ?";
    }

    private String renderInPredicate(
        Predicate.InPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        parameters.addAll(predicate.values());
        String placeholders = predicate.values().stream()
            .map(parameter -> "?")
            .collect(Collectors.joining(", "));
        return renderColumn(predicate.column(), columnRenderMode) + " in (" + placeholders + ")";
    }

    private String renderStringPredicate(
        Predicate.StringPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        parameters.add(Parameter.of(renderStringPattern(predicate.operator(), predicate.value())));
        String operator = predicate.comparison().caseStrategy() == StringCaseStrategy.INSENSITIVE ? "ilike" : "like";

        return renderStringColumn(predicate, columnRenderMode) + " " + operator + " ?";
    }

    private String renderRawSqlPredicate(Predicate.RawSqlPredicate predicate, List<Parameter> parameters) {
        parameters.addAll(predicate.parameters());
        return predicate.sql();
    }

    private String renderDialectPredicate(
        Predicate.DialectPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        if (!PostgresPredicates.DIALECT.equals(predicate.dialect())) {
            throw new IllegalArgumentException("Unsupported dialect predicate: " + predicate.dialect());
        }

        return switch (predicate.operator()) {
            case PostgresPredicates.ARRAY_CONTAINS -> renderArrayPredicate("@>", predicate, parameters, columnRenderMode);
            case PostgresPredicates.ARRAY_OVERLAPS -> renderArrayPredicate("&&", predicate, parameters, columnRenderMode);
            case PostgresPredicates.JSONB_CONTAINS -> renderJsonbContainsPredicate(predicate, parameters, columnRenderMode);
            case PostgresPredicates.FULL_TEXT_WEBSEARCH -> renderFullTextWebSearchPredicate(
                predicate,
                parameters,
                columnRenderMode
            );
            default -> throw new IllegalArgumentException("Unsupported PostgreSQL predicate: " + predicate.operator());
        };
    }

    private String renderArrayPredicate(
        String operator,
        Predicate.DialectPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        parameters.addAll(predicate.parameters());
        String placeholders = predicate.parameters().stream()
            .map(parameter -> "?")
            .collect(Collectors.joining(", "));
        return renderColumn(predicate.column(), columnRenderMode)
            + " "
            + operator
            + " array["
            + placeholders
            + "]::"
            + postgresArrayType(predicate.column());
    }

    private String renderJsonbContainsPredicate(
        Predicate.DialectPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        ensureSingleParameter(predicate);
        parameters.add(predicate.parameters().getFirst());
        return renderColumn(predicate.column(), columnRenderMode) + " @> ?::jsonb";
    }

    private String renderFullTextWebSearchPredicate(
        Predicate.DialectPredicate predicate,
        List<Parameter> parameters,
        ColumnRenderMode columnRenderMode
    ) {
        ensureSingleParameter(predicate);
        String config = predicate.options().get(PostgresPredicates.TEXT_SEARCH_CONFIG);
        if (config == null || !config.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL text search configuration: " + config);
        }
        parameters.add(predicate.parameters().getFirst());
        return "to_tsvector('"
            + config
            + "', "
            + renderColumn(predicate.column(), columnRenderMode)
            + ") @@ websearch_to_tsquery('"
            + config
            + "', ?)";
    }

    private void ensureSingleParameter(Predicate.DialectPredicate predicate) {
        if (predicate.parameters().size() != 1) {
            throw new IllegalArgumentException("Predicate requires exactly one parameter: " + predicate.operator());
        }
    }

    private String postgresArrayType(ColumnRef<?> column) {
        Class<?> javaType = column.javaType();
        if (!javaType.isArray()) {
            throw new IllegalArgumentException("PostgreSQL array predicates require an array column: " + column.propertyName());
        }

        Class<?> componentType = javaType.getComponentType();
        if (String.class.equals(componentType)) {
            return "text[]";
        }
        if (Long.class.equals(componentType) || Long.TYPE.equals(componentType)) {
            return "bigint[]";
        }
        if (Integer.class.equals(componentType) || Integer.TYPE.equals(componentType)) {
            return "integer[]";
        }
        if (Boolean.class.equals(componentType) || Boolean.TYPE.equals(componentType)) {
            return "boolean[]";
        }
        if (UUID.class.equals(componentType)) {
            return "uuid[]";
        }

        throw new IllegalArgumentException("Unsupported PostgreSQL array component type: " + componentType.getName());
    }

    private String renderStringColumn(Predicate.StringPredicate predicate, ColumnRenderMode columnRenderMode) {
        String renderedColumn = renderColumn(predicate.column(), columnRenderMode);
        return predicate.comparison().collation()
            .map(collation -> renderedColumn + " collate " + quotedIdentifier(collation))
            .orElse(renderedColumn);
    }

    private String renderStringPattern(StringOperator operator, String value) {
        return switch (operator) {
            case CONTAINS -> "%" + value + "%";
            case STARTS_WITH -> value + "%";
            case ENDS_WITH -> "%" + value;
        };
    }

    private String renderSort(Sort sort) {
        return renderColumn(sort.column()) + " " + sort.direction().name().toLowerCase(Locale.ROOT);
    }

    private String renderJoin(Join join) {
        return renderJoinType(join.type())
            + " join "
            + identifier(join.table().tableName())
            + " "
            + identifier(join.table().alias())
            + " on "
            + renderColumn(join.leftColumn())
            + " = "
            + renderColumn(join.rightColumn());
    }

    private String renderJoinType(JoinType joinType) {
        return switch (joinType) {
            case INNER -> "inner";
            case LEFT -> "left";
        };
    }

    private String renderColumn(ColumnRef<?> column) {
        return renderColumn(column, ColumnRenderMode.ALIASED);
    }

    private String renderColumn(ColumnRef<?> column, ColumnRenderMode columnRenderMode) {
        if (columnRenderMode == ColumnRenderMode.BARE) {
            return identifier(column.columnName());
        }

        return identifier(column.table().alias()) + "." + identifier(column.columnName());
    }

    private String renderAssignment(Assignment<?> assignment, List<Parameter> parameters) {
        parameters.add(assignment.value());
        return renderColumn(assignment.column(), ColumnRenderMode.BARE) + " = ?";
    }

    private void appendReturning(StringBuilder sql, List<ColumnRef<?>> columns) {
        if (columns.isEmpty()) {
            return;
        }

        appendClause(sql, "returning ");
        sql.append(columns.stream()
            .map(column -> renderColumn(column, ColumnRenderMode.BARE))
            .collect(Collectors.joining(", ")));
    }

    private void appendClause(StringBuilder sql, String clause) {
        sql.append(sqlFormat == PostgresSqlFormat.PRETTY ? "\n" : " ");
        sql.append(clause);
    }

    private String identifier(String value) {
        if (!value.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + value);
        }
        return value;
    }

    private String quotedIdentifier(String value) {
        if (!value.matches("[a-zA-Z0-9_\\-]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + value);
        }
        return "\"" + value + "\"";
    }

    private enum ColumnRenderMode {
        ALIASED,
        BARE
    }
}
