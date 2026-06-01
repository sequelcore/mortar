package dev.mortar.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.core.Parameter;
import dev.mortar.core.Projection;
import dev.mortar.core.ProjectionKind;
import dev.mortar.core.Assignment;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.QueryMetadata;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class MortarJdbcClientTest {
    @Test
    void exposesUncheckedExceptionTypeForAdapterFailures() {
        MortarJdbcException exception = new MortarJdbcException(
            "Failed",
            new IllegalStateException("database unavailable")
        );

        assertThat(exception)
            .hasMessage("Failed")
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void renderedQueryDefensivelyCopiesParameters() {
        RenderedQuery query = new RenderedQuery("select 1", List.of());

        assertThat(query.parameters()).isEmpty();
    }

    @Test
    void executesRenderedQueryAndMapsRows() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery("select name from clients where id = ?", List.of(dev.mortar.core.Parameter.of(7L)))
        );

        List<String> rows = client.fetch(
            new SimpleMortarDb().from(clients).build(),
            resultSet -> resultSet.getString("name")
        );

        assertThat(rows).containsExactly("Ricardo");
        assertThat(statement.sql).isEqualTo("select name from clients where id = ?");
        assertThat(statement.boundValues).containsExactly(7L);
    }

    @Test
    void executesPreRenderedQueryWithoutCallingRenderer() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> {
                throw new AssertionError("renderer should not run for pre-rendered queries");
            }
        );

        List<String> rows = client.fetch(
            new RenderedQuery("select name from clients where id = ?", List.of(Parameter.of(7L))),
            resultSet -> resultSet.getString("name")
        );

        assertThat(rows).containsExactly("Ricardo");
        assertThat(statement.sql).isEqualTo("select name from clients where id = ?");
        assertThat(statement.boundValues).containsExactly(7L);
    }

    @Test
    void fetchOptionalMapsSingleRenderedRowWithoutCallingRenderer() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> {
                throw new AssertionError("renderer should not run for pre-rendered queries");
            }
        );

        Optional<String> row = client.fetchOptional(
            new RenderedQuery("select name from clients where id = ?", List.of(Parameter.of(7L))),
            resultSet -> resultSet.getString("name")
        );

        assertThat(row).contains("Ricardo");
        assertThat(statement.sql).isEqualTo("select name from clients where id = ?");
        assertThat(statement.boundValues).containsExactly(7L);
    }

    @Test
    void executesGeneratedQueryWithDirectBinderAndMapperWithoutCallingRenderer() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> {
                throw new AssertionError("renderer should not run for generated queries");
            }
        );

        List<ClientRow> rows = client.fetch(new ClientLookupQuery(), new ClientLookup(true, 7L));

        assertThat(rows).containsExactly(new ClientRow(7L, "Ricardo"));
        assertThat(statement.sql).isEqualTo("select id, name from clients where active = ? and id = ?");
        assertThat(statement.boundCalls).containsExactly("setBoolean:1:true", "setLong:2:7");
    }

    @Test
    void executesGeneratedQueryWithoutParameters() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> {
                throw new AssertionError("renderer should not run for generated queries");
            }
        );

        List<ClientRow> rows = client.fetch(new ClientFindAllQuery());

        assertThat(rows).containsExactly(new ClientRow(7L, "Ricardo"));
        assertThat(statement.sql).isEqualTo("select id, name from clients");
        assertThat(statement.boundCalls).isEmpty();
    }

    @Test
    void fetchOptionalGeneratedQueryRejectsMultipleRows() {
        CapturingStatement statement = new CapturingStatement();
        statement.rowCount = 2;
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetchOptional(new ClientLookupQuery(), new ClientLookup(true, 7L))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected at most one row");
    }

    @Test
    void logsGeneratedQueryWithRedactedParameterTypes() {
        CapturingStatement statement = new CapturingStatement();
        CapturingJdbcLogger logger = new CapturingJdbcLogger();
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer(), logger);

        client.fetchOptional(new ClientLookupQuery(), new ClientLookup(true, 7L));

        assertThat(logger.events).containsExactly(
            new MortarJdbcLogEvent(
                MortarJdbcOperation.QUERY,
                "select id, name from clients where active = ? and id = ?",
                List.of(MortarJdbcParameter.redacted(Boolean.class), MortarJdbcParameter.redacted(Long.class)),
                ClientLookupQuery.METADATA
            )
        );
    }

    @Test
    void preparesGeneratedQueryOnCallerOwnedConnectionAndReusesStatement() throws Exception {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(connection(statement), queryRenderer());

        try (MortarPreparedQuery<ClientLookup, ClientRow> prepared = client.prepare(new ClientLookupQuery())) {
            assertThat(prepared.fetchOptional(new ClientLookup(true, 7L))).contains(new ClientRow(7L, "Ricardo"));
            assertThat(prepared.fetchOptional(new ClientLookup(true, 7L))).contains(new ClientRow(7L, "Ricardo"));
        }

        assertThat(statement.prepareStatementCalls).isEqualTo(1);
        assertThat(statement.executeQueryCalls).isEqualTo(2);
        assertThat(statement.preparedStatementCloseCalls).isEqualTo(1);
        assertThat(statement.boundCalls).containsExactly(
            "setBoolean:1:true",
            "setLong:2:7",
            "setBoolean:1:true",
            "setLong:2:7"
        );
    }

    @Test
    void rejectsPreparedGeneratedQueryForDataSourceOwnedConnections() {
        MortarJdbcClient client = new MortarJdbcClient(dataSource(new CapturingStatement()), queryRenderer());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.prepare(new ClientLookupQuery())
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("prepared generated queries require a caller-owned connection");
    }

    @Test
    void fetchOptionalReturnsEmptyWhenNoRowsExist() {
        CapturingStatement statement = new CapturingStatement();
        statement.rowCount = 0;
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer());

        Optional<String> row = client.fetchOptional(
            new SimpleMortarDb().from(new TableRef("clients", "c")).build(),
            resultSet -> resultSet.getString("name")
        );

        assertThat(row).isEmpty();
    }

    @Test
    void fetchOptionalRejectsMultipleRows() {
        CapturingStatement statement = new CapturingStatement();
        statement.rowCount = 2;
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetchOptional(
                    new SimpleMortarDb().from(new TableRef("clients", "c")).build(),
                    resultSet -> resultSet.getString("name")
                )
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected at most one row");
    }

    @Test
    void logsRenderedQueryWithRedactedParameters() {
        CapturingStatement statement = new CapturingStatement();
        CapturingJdbcLogger logger = new CapturingJdbcLogger();
        TableRef clients = new TableRef("clients", "c");
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> new RenderedQuery(
                "select name from clients where email = ?",
                List.of(Parameter.of("ada@example.com")),
                new QueryMetadata(List.of(clients), List.of(), List.of())
            ),
            logger
        );

        client.fetch(new SimpleMortarDb().from(clients).build(), resultSet -> resultSet.getString("name"));

        assertThat(logger.events).containsExactly(
            new MortarJdbcLogEvent(
                MortarJdbcOperation.QUERY,
                "select name from clients where email = ?",
                List.of(MortarJdbcParameter.redacted(String.class)),
                new QueryMetadata(List.of(clients), List.of(), List.of())
            )
        );
    }

    @Test
    void noopLoggerIsSingletonForHotPathDetection() {
        assertThat(MortarJdbcLogger.noop()).isSameAs(MortarJdbcLogger.noop());
    }

    @Test
    void logsBatchWithRedactedParameters() {
        CapturingStatement statement = new CapturingStatement();
        CapturingJdbcLogger logger = new CapturingJdbcLogger();
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer(), logger);

        client.executeBatch(List.of(new InsertSpec(clients, List.of(Assignment.of(id, 1L)), List.of())));

        assertThat(logger.events)
            .extracting(MortarJdbcLogEvent::operation)
            .containsExactly(MortarJdbcOperation.BATCH);
        assertThat(logger.events.getFirst().parameters()).containsExactly(MortarJdbcParameter.redacted(Long.class));
    }

    @Test
    void participatesInCallerOwnedConnectionWithoutClosingOrCommitting() throws Exception {
        CapturingStatement statement = new CapturingStatement();
        Connection connection = connection(statement);
        TableRef clients = new TableRef("clients", "c");
        MortarJdbcClient client = new MortarJdbcClient(
            connection,
            query -> new RenderedQuery("select name from clients where id = ?", List.of(Parameter.of(7L)))
        );

        List<String> rows = client.fetch(
            new SimpleMortarDb().from(clients).build(),
            resultSet -> resultSet.getString("name")
        );

        assertThat(rows).containsExactly("Ricardo");
        assertThat(statement.connectionCloseCalls).isZero();
        assertThat(statement.connectionCommitCalls).isZero();
        assertThat(statement.connectionRollbackCalls).isZero();
    }

    @Test
    void mapsProjectedRowsToRecordConstructor() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery("select id, name from clients", List.of())
        );

        List<ClientRow> rows = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .project(Projection.record(ClientRow.class, List.of(id, name)))
                .build(),
            ClientRow.class
        );

        assertThat(rows).containsExactly(new ClientRow(7L, "Ricardo"));
    }

    @Test
    void executesBatchForMutationsWithSameRenderedSql() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            queryRenderer()
        );

        int[] counts = client.executeBatch(List.of(
            new InsertSpec(clients, List.of(Assignment.of(id, 1L), Assignment.of(name, "Ada")), List.of()),
            new InsertSpec(clients, List.of(Assignment.of(id, 2L), Assignment.of(name, "Grace")), List.of())
        ));

        assertThat(counts).containsExactly(1, 1);
        assertThat(statement.sql).isEqualTo("insert into clients (id, name) values (?, ?)");
        assertThat(statement.boundCalls).containsExactly(
            "setLong:1:1",
            "setString:2:Ada",
            "setLong:1:2",
            "setString:2:Grace"
        );
        assertThat(statement.addBatchCalls).isEqualTo(2);
        assertThat(statement.executeBatchCalls).isEqualTo(1);
    }

    @Test
    void executeBatchRejectsDifferentRenderedSql() {
        CapturingStatement statement = new CapturingStatement();
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.executeBatch(List.of(
                    new InsertSpec(clients, List.of(Assignment.of(id, 1L)), List.of()),
                    new InsertSpec(clients, List.of(Assignment.of(id, 2L), Assignment.of(name, "Grace")), List.of())
                ))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("all batch statements must render to the same SQL");
    }

    @Test
    void mapsProjectedRowsToDtoConstructor() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery("select id, name from clients", List.of())
        );

        List<ClientDto> rows = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .project(Projection.dto(ClientDto.class, List.of(id, name)))
                .build(),
            ClientDto.class
        );

        assertThat(rows).containsExactly(new ClientDto(7L, "Ricardo"));
    }

    @Test
    void projectedFetchRejectsMissingProjection() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> new RenderedQuery("select id, name from clients", List.of())
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(
                    new SimpleMortarDb().from(new TableRef("clients", "c")).build(),
                    ClientRow.class
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("query projection is required for constructor mapping");
    }

    @Test
    void projectedFetchRejectsUnsupportedOrMismatchedProjection() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> new RenderedQuery("select id, name from clients", List.of())
        );
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<String> name = clients.column("name", "name", String.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(
                    new SimpleMortarDb()
                        .from(clients)
                        .project(Projection.scalar(name))
                        .build(),
                    String.class
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("constructor mapping requires a record or DTO projection");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(
                    new SimpleMortarDb()
                        .from(clients)
                        .project(Projection.record(ClientRow.class, List.of(id, name)))
                        .build(),
                    ClientDto.class
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("projection target type does not match requested type");
    }

    @Test
    void projectedFetchRejectsConstructorShapeMismatch() {
        CapturingStatement statement = new CapturingStatement();
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource(statement),
            query -> new RenderedQuery("select id from clients", List.of())
        );
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(
                    new SimpleMortarDb()
                        .from(clients)
                        .project(Projection.record(ClientRow.class, List.of(id)))
                        .build(),
                    ClientRow.class
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("record component count does not match projection columns for dev.mortar.jdbc.MortarJdbcClientTest$ClientRow");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(
                    new SimpleMortarDb()
                        .from(clients)
                        .project(Projection.dto(AmbiguousDto.class, List.of(id)))
                        .build(),
                    AmbiguousDto.class
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expected exactly one constructor with 1 parameters for dev.mortar.jdbc.MortarJdbcClientTest$AmbiguousDto");
    }

    @Test
    void bindsParametersByJavaType() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery(
                "select name from clients where name = ? and id = ? and route_id is ? and active = ? and created_on = ?",
                List.of(
                    Parameter.of("Ada"),
                    Parameter.of(7L),
                    new Parameter(null, Long.class),
                    Parameter.of(true),
                    Parameter.of(LocalDate.of(2026, 5, 31))
                )
            )
        );

        client.fetch(new SimpleMortarDb().from(clients).build(), resultSet -> resultSet.getString("name"));

        assertThat(statement.boundCalls).containsExactly(
            "setString:1:Ada",
            "setLong:2:7",
            "setNull:3:" + Types.BIGINT,
            "setBoolean:4:true",
            "setDate:5:2026-05-31"
        );
    }

    @Test
    void bindsAdditionalSupportedTypesAndFallsBackToSetObject() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        UUID uuid = UUID.fromString("04aa0d41-1ad1-45dc-8013-7c78c18da820");
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery(
                "select name from clients where score = ? and amount = ? and created_at = ? and seen_at = ? and external_id = ?",
                List.of(
                    Parameter.of(11),
                    Parameter.of(new BigDecimal("19.95")),
                    Parameter.of(LocalDateTime.of(2026, 5, 31, 10, 15, 30)),
                    Parameter.of(Instant.parse("2026-05-31T10:15:30Z")),
                    Parameter.of(uuid)
                )
            )
        );

        client.fetch(new SimpleMortarDb().from(clients).build(), resultSet -> resultSet.getString("name"));

        assertThat(statement.boundMethods).containsExactly(
            "setInt",
            "setBigDecimal",
            "setTimestamp",
            "setTimestamp",
            "setObject"
        );
    }

    @Test
    void mapsJdbcValuesToConstructorParameterTypes() {
        CapturingStatement statement = new CapturingStatement();
        statement.rowValues.put("id", Integer.valueOf(7));
        statement.rowValues.put("created_on", java.sql.Date.valueOf(LocalDate.of(2026, 5, 31)));
        statement.rowValues.put("created_at", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 5, 31, 10, 15, 30)));
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        dev.mortar.core.ColumnRef<LocalDate> createdOn = clients.column("createdOn", "created_on", LocalDate.class);
        dev.mortar.core.ColumnRef<LocalDateTime> createdAt = clients.column("createdAt", "created_at", LocalDateTime.class);
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery("select id, created_on, created_at from clients", List.of())
        );

        List<TypedRow> rows = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .project(new Projection(ProjectionKind.DTO, java.util.Optional.of(TypedRow.class), List.of(id, createdOn, createdAt), List.of()))
                .build(),
            TypedRow.class
        );

        assertThat(rows).containsExactly(
            new TypedRow(7L, LocalDate.of(2026, 5, 31), LocalDateTime.of(2026, 5, 31, 10, 15, 30))
        );
    }

    @Test
    void bindsNullParametersWithSqlTypeFromJavaType() {
        CapturingStatement statement = new CapturingStatement();
        DataSource dataSource = dataSource(statement);
        TableRef clients = new TableRef("clients", "c");
        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> new RenderedQuery(
                "select name from clients where name is ? and active is ? and created_at is ? and payload is ?",
                List.of(
                    new Parameter(null, String.class),
                    new Parameter(null, Boolean.class),
                    new Parameter(null, LocalDateTime.class),
                    new Parameter(null, Object.class)
                )
            )
        );

        client.fetch(new SimpleMortarDb().from(clients).build(), resultSet -> resultSet.getString("name"));

        assertThat(statement.boundCalls).containsExactly(
            "setNull:1:" + Types.VARCHAR,
            "setNull:2:" + Types.BOOLEAN,
            "setNull:3:" + Types.TIMESTAMP,
            "setNull:4:" + Types.JAVA_OBJECT
        );
    }

    @Test
    void wrapsSqlExceptions() {
        RenderedQuery renderedQuery = new RenderedQuery(
            "select 1 where id = ?",
            List.of(Parameter.of(7L)),
            new QueryMetadata(List.of(new TableRef("clients", "c")), List.of(), List.of())
        );
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { DataSource.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    throw new SQLException("down");
                }
                return defaultValue(method.getReturnType());
            }
        );

        MortarJdbcClient client = new MortarJdbcClient(
            dataSource,
            query -> renderedQuery
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.fetch(new SimpleMortarDb().from(new TableRef("clients", "c")).build(), resultSet -> "x")
            )
            .isInstanceOf(MortarJdbcException.class)
            .hasMessage("Failed to execute Mortar query")
            .hasCauseInstanceOf(SQLException.class)
            .satisfies(exception -> {
                MortarJdbcException jdbcException = (MortarJdbcException) exception;
                assertThat(jdbcException.sql()).contains("select 1 where id = ?");
                assertThat(jdbcException.parameters()).extracting(Parameter::value).containsExactly(7L);
                assertThat(jdbcException.metadata().tables()).containsExactly(new TableRef("clients", "c"));
            });
    }

    @Test
    void wrapsBatchSqlExceptionsWithRenderedQueryContext() {
        CapturingStatement statement = new CapturingStatement();
        statement.failExecuteBatch = true;
        TableRef clients = new TableRef("clients", "c");
        dev.mortar.core.ColumnRef<Long> id = clients.column("id", "id", Long.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource(statement), queryRenderer());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.executeBatch(List.of(new InsertSpec(clients, List.of(Assignment.of(id, 1L)), List.of())))
            )
            .isInstanceOf(MortarJdbcException.class)
            .hasMessage("Failed to execute Mortar batch")
            .hasCauseInstanceOf(SQLException.class)
            .satisfies(exception -> {
                MortarJdbcException jdbcException = (MortarJdbcException) exception;
                assertThat(jdbcException.sql()).contains("insert into clients (id) values (?)");
                assertThat(jdbcException.parameters()).extracting(Parameter::value).containsExactly(1L);
            });
    }

    private DataSource dataSource(CapturingStatement statement) {
        return (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { DataSource.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return connection(statement);
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private Connection connection(CapturingStatement statement) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    statement.sql = (String) args[0];
                    statement.prepareStatementCalls++;
                    return preparedStatement(statement);
                }
                if (method.getName().equals("close")) {
                    statement.connectionCloseCalls++;
                    return null;
                }
                if (method.getName().equals("commit")) {
                    statement.connectionCommitCalls++;
                    return null;
                }
                if (method.getName().equals("rollback")) {
                    statement.connectionRollbackCalls++;
                    return null;
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private PreparedStatement preparedStatement(CapturingStatement statement) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { PreparedStatement.class },
            preparedStatementHandler(statement)
        );
    }

    private InvocationHandler preparedStatementHandler(CapturingStatement statement) {
        return (proxy, method, args) -> {
            if (method.getName().startsWith("set")) {
                statement.boundMethods.add(method.getName());
                statement.boundCalls.add(method.getName() + ":" + args[0] + ":" + args[1]);
                statement.boundValues.add(args[1]);
                return null;
            }
            if (method.getName().equals("executeQuery")) {
                statement.executeQueryCalls++;
                return resultSet(statement);
            }
            if (method.getName().equals("close")) {
                statement.preparedStatementCloseCalls++;
                return null;
            }
            if (method.getName().equals("addBatch")) {
                statement.addBatchCalls++;
                return null;
            }
            if (method.getName().equals("executeBatch")) {
                if (statement.failExecuteBatch) {
                    throw new SQLException("batch failed");
                }
                statement.executeBatchCalls++;
                return new int[] { 1, 1 };
            }
            return defaultValue(method.getReturnType());
        };
    }

    private dev.mortar.core.QueryRenderer queryRenderer() {
        return new dev.mortar.core.QueryRenderer() {
            @Override
            public RenderedQuery render(dev.mortar.core.QuerySpec query) {
                return new RenderedQuery("select 1", List.of());
            }

            @Override
            public RenderedQuery render(InsertSpec insert) {
                return new RenderedQuery(
                    "insert into clients ("
                        + insert.assignments().stream()
                            .map(assignment -> assignment.column().columnName())
                            .collect(java.util.stream.Collectors.joining(", "))
                        + ") values ("
                        + insert.assignments().stream()
                            .map(assignment -> "?")
                            .collect(java.util.stream.Collectors.joining(", "))
                        + ")",
                    insert.assignments().stream()
                        .map(Assignment::value)
                        .toList()
                );
            }
        };
    }

    private ResultSet resultSet(CapturingStatement statement) {
        return (ResultSet) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { ResultSet.class },
            new InvocationHandler() {
                private int cursor;

                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    if (method.getName().equals("next")) {
                        cursor++;
                        return cursor <= statement.rowCount;
                    }
                    if (method.getName().equals("getString")) {
                        return "Ricardo";
                    }
                    if (method.getName().equals("getLong")) {
                        return 7L;
                    }
                    if (method.getName().equals("getObject")) {
                        return statement.rowValues.get(args[0]);
                    }
                    return defaultValue(method.getReturnType());
                }
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }

    private static final class CapturingStatement {
        private String sql;
        private final java.util.ArrayList<Object> boundValues = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> boundCalls = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> boundMethods = new java.util.ArrayList<>();
        private final java.util.Map<String, Object> rowValues = new java.util.HashMap<>(
            java.util.Map.of("id", 7L, "name", "Ricardo")
        );
        private int addBatchCalls;
        private int executeBatchCalls;
        private int executeQueryCalls;
        private int prepareStatementCalls;
        private int preparedStatementCloseCalls;
        private int connectionCloseCalls;
        private int connectionCommitCalls;
        private int connectionRollbackCalls;
        private int rowCount = 1;
        private boolean failExecuteBatch;
    }

    private record ClientLookup(boolean active, long id) {
    }

    private record ClientRow(Long id, String name) {
    }

    private record ClientDto(Long id, String name) {
    }

    private record TypedRow(Long id, LocalDate createdOn, LocalDateTime createdAt) {
    }

    private static final class AmbiguousDto {
        private AmbiguousDto() {
        }

        private AmbiguousDto(Long id, String name) {
        }
    }

    private static final class CapturingJdbcLogger implements MortarJdbcLogger {
        private final java.util.ArrayList<MortarJdbcLogEvent> events = new java.util.ArrayList<>();

        @Override
        public void log(MortarJdbcLogEvent event) {
            events.add(event);
        }
    }

    private static final class ClientLookupQuery implements MortarGeneratedQuery<ClientLookup, ClientRow> {
        private static final TableRef CLIENTS = new TableRef("clients", "c");
        private static final dev.mortar.core.ColumnRef<Long> ID = CLIENTS.column("id", "id", Long.class);
        private static final dev.mortar.core.ColumnRef<String> NAME = CLIENTS.column("name", "name", String.class);
        private static final dev.mortar.core.ColumnRef<Boolean> ACTIVE = CLIENTS.column("active", "active", Boolean.class);
        private static final List<TableRef> CLIENTS_LIST = List.of(CLIENTS);
        private static final List<dev.mortar.core.ColumnRef<?>> COLUMNS = List.of(ID, NAME, ACTIVE);
        private static final QueryMetadata METADATA = new QueryMetadata(CLIENTS_LIST, COLUMNS, List.of());

        @Override
        public String sql() {
            return "select id, name from clients where active = ? and id = ?";
        }

        @Override
        public List<Class<?>> parameterTypes() {
            return List.of(Boolean.class, Long.class);
        }

        @Override
        public QueryMetadata metadata() {
            return METADATA;
        }

        @Override
        public void bind(PreparedStatement statement, ClientLookup parameters) throws SQLException {
            statement.setBoolean(1, parameters.active());
            statement.setLong(2, parameters.id());
        }

        @Override
        public ClientRow map(ResultSet resultSet) throws SQLException {
            return new ClientRow(resultSet.getLong("id"), resultSet.getString("name"));
        }
    }

    private static final class ClientFindAllQuery implements MortarGeneratedQuery<MortarNoParameters, ClientRow> {
        @Override
        public String sql() {
            return "select id, name from clients";
        }

        @Override
        public void bind(PreparedStatement statement, MortarNoParameters parameters) {
            java.util.Objects.requireNonNull(statement, "statement cannot be null");
            java.util.Objects.requireNonNull(parameters, "parameters cannot be null");
        }

        @Override
        public ClientRow map(ResultSet resultSet) throws SQLException {
            return new ClientRow(resultSet.getLong("id"), resultSet.getString("name"));
        }
    }
}
