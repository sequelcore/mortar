package dev.mortar.jdbc;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.MutationSpec;
import dev.mortar.core.Parameter;
import dev.mortar.core.Projection;
import dev.mortar.core.ProjectionKind;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.UpdateSpec;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter that executes rendered and generated Mortar query plans.
 */
public final class MortarJdbcClient {
    private final DataSource dataSource;
    private final Connection connection;
    private final QueryRenderer renderer;
    private final MortarJdbcLogger logger;
    private final boolean loggingEnabled;

    public MortarJdbcClient(DataSource dataSource, QueryRenderer renderer) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.connection = null;
        this.renderer = Objects.requireNonNull(renderer, "renderer cannot be null");
        this.logger = MortarJdbcLogger.noop();
        this.loggingEnabled = false;
    }

    public MortarJdbcClient(DataSource dataSource, QueryRenderer renderer, MortarJdbcLogger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.connection = null;
        this.renderer = Objects.requireNonNull(renderer, "renderer cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.loggingEnabled = logger != MortarJdbcLogger.noop();
    }

    public MortarJdbcClient(Connection connection, QueryRenderer renderer) {
        this.dataSource = null;
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer cannot be null");
        this.logger = MortarJdbcLogger.noop();
        this.loggingEnabled = false;
    }

    public MortarJdbcClient(Connection connection, QueryRenderer renderer, MortarJdbcLogger logger) {
        this.dataSource = null;
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.loggingEnabled = logger != MortarJdbcLogger.noop();
    }

    public <T> List<T> fetch(QuerySpec query, RowMapper<T> mapper) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(mapper, "mapper cannot be null");

        RenderedQuery rendered = renderer.render(query);
        return fetchRendered(rendered, mapper);
    }

    public <T> List<T> fetch(RenderedQuery renderedQuery, RowMapper<T> mapper) {
        Objects.requireNonNull(renderedQuery, "renderedQuery cannot be null");
        Objects.requireNonNull(mapper, "mapper cannot be null");

        return fetchRendered(renderedQuery, mapper);
    }

    public <T> Optional<T> fetchOptional(QuerySpec query, RowMapper<T> mapper) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(mapper, "mapper cannot be null");

        RenderedQuery rendered = renderer.render(query);
        return fetchOptionalRendered(rendered, mapper);
    }

    public <T> Optional<T> fetchOptional(RenderedQuery renderedQuery, RowMapper<T> mapper) {
        Objects.requireNonNull(renderedQuery, "renderedQuery cannot be null");
        Objects.requireNonNull(mapper, "mapper cannot be null");

        return fetchOptionalRendered(renderedQuery, mapper);
    }

    public <P, T> List<T> fetch(MortarGeneratedQuery<P, T> query, P parameters) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");

        return fetchGenerated(query, parameters);
    }

    /**
     * Executes a generated query that has no caller-supplied parameters.
     */
    public <T> List<T> fetch(MortarGeneratedQuery<MortarNoParameters, T> query) {
        Objects.requireNonNull(query, "query cannot be null");

        return fetchGenerated(query, MortarNoParameters.INSTANCE);
    }

    public <P, T> Optional<T> fetchOptional(MortarGeneratedQuery<P, T> query, P parameters) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");

        return fetchOptionalGenerated(query, parameters);
    }

    /**
     * Executes an at-most-one-row generated query that has no caller-supplied parameters.
     */
    public <T> Optional<T> fetchOptional(MortarGeneratedQuery<MortarNoParameters, T> query) {
        Objects.requireNonNull(query, "query cannot be null");

        return fetchOptionalGenerated(query, MortarNoParameters.INSTANCE);
    }

    public <P, T> MortarPreparedQuery<P, T> prepare(MortarGeneratedQuery<P, T> query) {
        Objects.requireNonNull(query, "query cannot be null");
        if (connection == null) {
            throw new IllegalStateException("prepared generated queries require a caller-owned connection");
        }

        try {
            return new MortarPreparedQuery<>(
                connection.prepareStatement(query.sql()),
                query,
                logger,
                loggingEnabled
            );
        } catch (SQLException exception) {
            throw new MortarJdbcException(
                "Failed to prepare Mortar generated query",
                MortarGeneratedQueryContext.rendered(query),
                exception
            );
        }
    }

    private <T> List<T> fetchRendered(RenderedQuery rendered, RowMapper<T> mapper) {
        log(MortarJdbcOperation.QUERY, rendered);

        try (
            ConnectionLease lease = connectionLease();
            PreparedStatement statement = lease.connection().prepareStatement(rendered.sql())
        ) {
            bind(statement, rendered.parameters());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException("Failed to execute Mortar query", rendered, exception);
        }
    }

    private <T> Optional<T> fetchOptionalRendered(RenderedQuery rendered, RowMapper<T> mapper) {
        log(MortarJdbcOperation.QUERY, rendered);

        try (
            ConnectionLease lease = connectionLease();
            PreparedStatement statement = lease.connection().prepareStatement(rendered.sql())
        ) {
            bind(statement, rendered.parameters());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                T row = mapper.map(resultSet);
                if (resultSet.next()) {
                    throw new IllegalStateException("expected at most one row");
                }
                return Optional.ofNullable(row);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException("Failed to execute Mortar query", rendered, exception);
        }
    }

    private <P, T> List<T> fetchGenerated(MortarGeneratedQuery<P, T> query, P parameters) {
        log(MortarJdbcOperation.QUERY, query);

        try (
            ConnectionLease lease = connectionLease();
            PreparedStatement statement = lease.connection().prepareStatement(query.sql())
        ) {
            query.bind(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(query.map(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException(
                "Failed to execute Mortar generated query",
                MortarGeneratedQueryContext.rendered(query),
                exception
            );
        }
    }

    private <P, T> Optional<T> fetchOptionalGenerated(MortarGeneratedQuery<P, T> query, P parameters) {
        log(MortarJdbcOperation.QUERY, query);

        try (
            ConnectionLease lease = connectionLease();
            PreparedStatement statement = lease.connection().prepareStatement(query.sql())
        ) {
            query.bind(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                T row = query.map(resultSet);
                if (resultSet.next()) {
                    throw new IllegalStateException("expected at most one row");
                }
                return Optional.ofNullable(row);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException(
                "Failed to execute Mortar generated query",
                MortarGeneratedQueryContext.rendered(query),
                exception
            );
        }
    }

    public <T> List<T> fetch(QuerySpec query, Class<T> targetType) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(targetType, "targetType cannot be null");

        Projection projection = query.projection()
            .orElseThrow(() -> new IllegalArgumentException("query projection is required for constructor mapping"));
        if (projection.kind() != ProjectionKind.RECORD && projection.kind() != ProjectionKind.DTO) {
            throw new IllegalArgumentException("constructor mapping requires a record or DTO projection");
        }
        if (projection.targetType().isEmpty() || !projection.targetType().get().equals(targetType)) {
            throw new IllegalArgumentException("projection target type does not match requested type");
        }

        return fetch(query, new ConstructorRowMapper<>(targetType, projection.columns()));
    }

    public int[] executeBatch(List<? extends MutationSpec> mutations) {
        Objects.requireNonNull(mutations, "mutations cannot be null");
        if (mutations.isEmpty()) {
            throw new IllegalArgumentException("mutations cannot be empty");
        }

        List<RenderedQuery> renderedQueries = mutations.stream()
            .map(this::render)
            .toList();
        String sql = renderedQueries.getFirst().sql();
        boolean sameSql = renderedQueries.stream().allMatch(rendered -> rendered.sql().equals(sql));
        if (!sameSql) {
            throw new IllegalArgumentException("all batch statements must render to the same SQL");
        }
        log(MortarJdbcOperation.BATCH, renderedQueries.getFirst());

        try (
            ConnectionLease lease = connectionLease();
            PreparedStatement statement = lease.connection().prepareStatement(sql)
        ) {
            for (RenderedQuery renderedQuery : renderedQueries) {
                bind(statement, renderedQuery.parameters());
                statement.addBatch();
            }
            return statement.executeBatch();
        } catch (SQLException exception) {
            throw new MortarJdbcException("Failed to execute Mortar batch", renderedQueries.getFirst(), exception);
        }
    }

    private void bind(PreparedStatement statement, List<Parameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            bindParameter(statement, index + 1, parameters.get(index));
        }
    }

    private void log(MortarJdbcOperation operation, RenderedQuery renderedQuery) {
        if (!loggingEnabled) {
            return;
        }
        logger.log(new MortarJdbcLogEvent(
            operation,
            renderedQuery.sql(),
            renderedQuery.parameters().stream()
                .map(parameter -> MortarJdbcParameter.redacted(parameter.javaType()))
                .toList(),
            renderedQuery.metadata()
        ));
    }

    private void log(MortarJdbcOperation operation, MortarGeneratedQuery<?, ?> query) {
        if (!loggingEnabled) {
            return;
        }
        logger.log(new MortarJdbcLogEvent(
            operation,
            query.sql(),
            MortarGeneratedQueryContext.redactedParameters(query),
            query.metadata()
        ));
    }

    private ConnectionLease connectionLease() throws SQLException {
        if (connection != null) {
            return new ConnectionLease(connection, false);
        }
        return new ConnectionLease(dataSource.getConnection(), true);
    }

    private RenderedQuery render(MutationSpec mutation) {
        return switch (mutation) {
            case InsertSpec insert -> renderer.render(insert);
            case UpdateSpec update -> renderer.render(update);
            case DeleteSpec delete -> renderer.render(delete);
        };
    }

    private void bindParameter(PreparedStatement statement, int index, Parameter parameter) throws SQLException {
        Object value = parameter.value();
        Class<?> javaType = parameter.javaType();

        if (value == null) {
            statement.setNull(index, sqlType(javaType));
            return;
        }

        if (String.class.equals(javaType)) {
            statement.setString(index, (String) value);
            return;
        }
        if (Long.class.equals(javaType) || Long.TYPE.equals(javaType)) {
            statement.setLong(index, (Long) value);
            return;
        }
        if (Integer.class.equals(javaType) || Integer.TYPE.equals(javaType)) {
            statement.setInt(index, (Integer) value);
            return;
        }
        if (Boolean.class.equals(javaType) || Boolean.TYPE.equals(javaType)) {
            statement.setBoolean(index, (Boolean) value);
            return;
        }
        if (BigDecimal.class.equals(javaType)) {
            statement.setBigDecimal(index, (BigDecimal) value);
            return;
        }
        if (LocalDate.class.equals(javaType)) {
            statement.setDate(index, Date.valueOf((LocalDate) value));
            return;
        }
        if (LocalDateTime.class.equals(javaType)) {
            statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
            return;
        }
        if (Instant.class.equals(javaType)) {
            statement.setTimestamp(index, Timestamp.from((Instant) value));
            return;
        }

        statement.setObject(index, value);
    }

    private int sqlType(Class<?> javaType) {
        if (String.class.equals(javaType)) {
            return Types.VARCHAR;
        }
        if (Long.class.equals(javaType) || Long.TYPE.equals(javaType)) {
            return Types.BIGINT;
        }
        if (Integer.class.equals(javaType) || Integer.TYPE.equals(javaType)) {
            return Types.INTEGER;
        }
        if (Boolean.class.equals(javaType) || Boolean.TYPE.equals(javaType)) {
            return Types.BOOLEAN;
        }
        if (BigDecimal.class.equals(javaType)) {
            return Types.NUMERIC;
        }
        if (LocalDate.class.equals(javaType)) {
            return Types.DATE;
        }
        if (LocalDateTime.class.equals(javaType) || Instant.class.equals(javaType)) {
            return Types.TIMESTAMP;
        }
        return Types.JAVA_OBJECT;
    }

    private static final class ConstructorRowMapper<T> implements RowMapper<T> {
        private final Class<T> targetType;
        private final List<ColumnRef<?>> columns;
        private final Constructor<T> constructor;
        private final Class<?>[] parameterTypes;

        private ConstructorRowMapper(Class<T> targetType, List<ColumnRef<?>> columns) {
            this.targetType = targetType;
            this.columns = List.copyOf(columns);
            this.constructor = constructor(targetType, columns.size());
            this.parameterTypes = constructor.getParameterTypes();
        }

        @Override
        public T map(ResultSet resultSet) throws SQLException {
            Object[] arguments = new Object[columns.size()];
            for (int index = 0; index < columns.size(); index++) {
                Object value = resultSet.getObject(columns.get(index).columnName());
                arguments[index] = convert(value, parameterTypes[index]);
            }

            try {
                return constructor.newInstance(arguments);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                throw new MortarJdbcException("Failed to map Mortar row to " + targetType.getName(), exception);
            }
        }

        private static <T> Constructor<T> constructor(Class<T> targetType, int parameterCount) {
            if (targetType.isRecord()) {
                return recordConstructor(targetType, parameterCount);
            }

            List<Constructor<?>> candidates = java.util.Arrays.stream(targetType.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == parameterCount)
                .toList();
            if (candidates.size() != 1) {
                throw new IllegalArgumentException(
                    "expected exactly one constructor with "
                        + parameterCount
                        + " parameters for "
                        + targetType.getName()
                );
            }
            @SuppressWarnings("unchecked")
            Constructor<T> constructor = (Constructor<T>) candidates.getFirst();
            constructor.setAccessible(true);
            return constructor;
        }

        private static <T> Constructor<T> recordConstructor(Class<T> targetType, int parameterCount) {
            RecordComponent[] components = targetType.getRecordComponents();
            if (components.length != parameterCount) {
                throw new IllegalArgumentException(
                    "record component count does not match projection columns for " + targetType.getName()
                );
            }
            Class<?>[] parameterTypes = java.util.Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
            try {
                Constructor<T> constructor = targetType.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor;
            } catch (NoSuchMethodException exception) {
                throw new IllegalArgumentException("record canonical constructor not found for " + targetType.getName(), exception);
            }
        }

        private Object convert(Object value, Class<?> targetType) {
            if (value == null) {
                return null;
            }
            if (targetType.isInstance(value)) {
                return value;
            }
            if ((Long.class.equals(targetType) || Long.TYPE.equals(targetType)) && value instanceof Number number) {
                return number.longValue();
            }
            if ((Integer.class.equals(targetType) || Integer.TYPE.equals(targetType)) && value instanceof Number number) {
                return number.intValue();
            }
            if (LocalDate.class.equals(targetType) && value instanceof Date date) {
                return date.toLocalDate();
            }
            if (LocalDateTime.class.equals(targetType) && value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            if (Instant.class.equals(targetType) && value instanceof Timestamp timestamp) {
                return timestamp.toInstant();
            }
            return value;
        }
    }

    private record ConnectionLease(Connection connection, boolean closeOnExit) implements AutoCloseable {
        private ConnectionLease {
            Objects.requireNonNull(connection, "connection cannot be null");
        }

        @Override
        public void close() throws SQLException {
            if (closeOnExit) {
                connection.close();
            }
        }
    }
}
