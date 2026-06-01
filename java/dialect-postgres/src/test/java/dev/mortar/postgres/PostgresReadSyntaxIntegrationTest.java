package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresReadSyntaxIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void executesPaginatedJoinAgainstPostgreSQL() throws Exception {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> clientId = clients.column("id", "id", Long.class);
        ColumnRef<String> clientName = clients.column("name", "name", String.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        ColumnRef<String> routeName = routes.column("name", "name", String.class);
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .innerJoin(routes, routeId, routeTableId)
                .select(clientId, clientName, routeName)
                .where(active.eq(true))
                .orderBy(clientId.asc())
                .limit(1)
                .offset(1)
                .build()
        );

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            createSchema(connection);
            seedData(connection);

            try (PreparedStatement statement = connection.prepareStatement(rendered.sql())) {
                for (int index = 0; index < rendered.parameters().size(); index++) {
                    statement.setObject(index + 1, rendered.parameters().get(index).value());
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getLong(1)).isEqualTo(2L);
                    assertThat(resultSet.getString(2)).isEqualTo("Beta");
                    assertThat(resultSet.getString(3)).isEqualTo("South");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists clients");
            statement.execute("drop table if exists routes");
            statement.execute("create table routes (id bigint primary key, name text not null)");
            statement.execute("""
                create table clients (
                    id bigint primary key,
                    route_id bigint not null references routes(id),
                    name text not null,
                    active boolean not null
                )
                """);
            statement.execute("create index clients_active_id_idx on clients (active, id)");
        }
    }

    private static void seedData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("insert into routes (id, name) values (10, 'North'), (20, 'South')");
            statement.execute("""
                insert into clients (id, route_id, name, active) values
                (1, 10, 'Alpha', true),
                (2, 20, 'Beta', true),
                (3, 20, 'Gamma', false)
                """);
        }
    }
}
