package dev.mortar.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Projection;
import dev.mortar.core.Assignment;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.postgres.PostgresQueryRenderer;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

@Testcontainers(disabledWithoutDocker = true)
final class MortarJdbcClientIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void executesRenderedPreparedStatementAndMapsScalarRowsAgainstPostgreSQL() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            createSchema(connection);
            seedClients(connection);
        }

        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ColumnRef<LocalDate> createdOn = clients.column("createdOn", "created_on", LocalDate.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource, new PostgresQueryRenderer());

        List<String> names = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .select(name)
                .where(id.eq(7L))
                .where(active.eq(true))
                .where(createdOn.eq(LocalDate.of(2026, 5, 31)))
                .build(),
            resultSet -> resultSet.getString("name")
        );

        assertThat(names).containsExactly("Ada");
    }

    @Test
    void mapsProjectedRowsToRecordAndDtoConstructorsAgainstPostgreSQL() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            createSchema(connection);
            seedClients(connection);
        }

        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource, new PostgresQueryRenderer());

        List<ClientRow> recordRows = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .project(Projection.record(ClientRow.class, List.of(id, name)))
                .where(id.eq(7L))
                .build(),
            ClientRow.class
        );
        List<ClientDto> dtoRows = client.fetch(
            new SimpleMortarDb()
                .from(clients)
                .project(Projection.dto(ClientDto.class, List.of(id, name)))
                .where(id.eq(8L))
                .build(),
            ClientDto.class
        );

        assertThat(recordRows).containsExactly(new ClientRow(7L, "Ada"));
        assertThat(dtoRows).containsExactly(new ClientDto(8L, "Grace"));
    }

    @Test
    void executesInsertBatchAgainstPostgreSQL() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            createSchema(connection);
        }

        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ColumnRef<java.time.LocalDate> createdOn = clients.column("createdOn", "created_on", java.time.LocalDate.class);
        MortarJdbcClient client = new MortarJdbcClient(dataSource, new PostgresQueryRenderer());

        int[] counts = client.executeBatch(List.of(
            new InsertSpec(
                clients,
                List.of(
                    Assignment.of(id, 9L),
                    Assignment.of(name, "Lin"),
                    Assignment.of(active, true),
                    Assignment.of(createdOn, java.time.LocalDate.of(2026, 5, 31))
                ),
                List.of()
            ),
            new InsertSpec(
                clients,
                List.of(
                    Assignment.of(id, 10L),
                    Assignment.of(name, "Katherine"),
                    Assignment.of(active, false),
                    Assignment.of(createdOn, java.time.LocalDate.of(2026, 5, 30))
                ),
                List.of()
            )
        ));

        assertThat(counts).containsExactly(1, 1);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery("select count(*) from clients")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void participatesInCallerOwnedTransactionWithoutCommitting() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection setup = dataSource.getConnection()) {
            createSchema(setup);
        }

        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ColumnRef<java.time.LocalDate> createdOn = clients.column("createdOn", "created_on", java.time.LocalDate.class);

        try (Connection transaction = dataSource.getConnection()) {
            transaction.setAutoCommit(false);
            MortarJdbcClient client = new MortarJdbcClient(transaction, new PostgresQueryRenderer());

            client.executeBatch(List.of(new InsertSpec(
                clients,
                List.of(
                    Assignment.of(id, 11L),
                    Assignment.of(name, "Rollback"),
                    Assignment.of(active, true),
                    Assignment.of(createdOn, java.time.LocalDate.of(2026, 5, 31))
                ),
                List.of()
            )));
            transaction.rollback();
        }

        try (Connection verify = dataSource.getConnection();
             Statement statement = verify.createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery("select count(*) from clients")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isZero();
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists clients");
            statement.execute(
                "create table clients (id bigint primary key, name text not null, active boolean not null, created_on date not null)"
            );
        }
    }

    private static void seedClients(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "insert into clients (id, name, active, created_on) values "
                    + "(7, 'Ada', true, date '2026-05-31'), "
                    + "(8, 'Grace', true, date '2026-05-30')"
            );
        }
    }

    private static DataSource dataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                return DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
                );
            }

            @Override
            public Connection getConnection(String username, String password) throws java.sql.SQLException {
                return DriverManager.getConnection(POSTGRES.getJdbcUrl(), username, password);
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getGlobal();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                throw new java.sql.SQLException("unwrap is not supported");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    private record ClientRow(Long id, String name) {
    }

    private record ClientDto(Long id, String name) {
    }
}
