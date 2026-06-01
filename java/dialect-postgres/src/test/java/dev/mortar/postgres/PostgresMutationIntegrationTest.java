package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.Assignment;
import dev.mortar.core.ColumnRef;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.TableRef;
import dev.mortar.core.UpdateSpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresMutationIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void executesRenderedMutationsAgainstPostgreSQL() throws Exception {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        PostgresQueryRenderer renderer = new PostgresQueryRenderer();

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            createSchema(connection);

            RenderedQuery insert = renderer.render(new InsertSpec(
                clients,
                List.of(Assignment.of(id, 1L), Assignment.of(name, "Ada")),
                List.of(id, name)
            ));
            assertReturnedClient(connection, insert, 1L, "Ada");

            RenderedQuery update = renderer.render(new UpdateSpec(
                clients,
                List.of(Assignment.of(name, "Grace")),
                List.of(id.eq(1L)),
                List.of(id, name)
            ));
            assertReturnedClient(connection, update, 1L, "Grace");

            RenderedQuery delete = renderer.render(new DeleteSpec(clients, List.of(id.eq(1L)), List.of(id)));
            assertReturnedId(connection, delete, 1L);
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists clients");
            statement.execute("create table clients (id bigint primary key, name text not null)");
        }
    }

    private static void assertReturnedClient(
        Connection connection,
        RenderedQuery renderedQuery,
        long expectedId,
        String expectedName
    ) throws Exception {
        try (PreparedStatement statement = prepare(connection, renderedQuery);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("id")).isEqualTo(expectedId);
            assertThat(resultSet.getString("name")).isEqualTo(expectedName);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static void assertReturnedId(Connection connection, RenderedQuery renderedQuery, long expectedId)
        throws Exception {
        try (PreparedStatement statement = prepare(connection, renderedQuery);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("id")).isEqualTo(expectedId);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static PreparedStatement prepare(Connection connection, RenderedQuery renderedQuery) throws Exception {
        PreparedStatement statement = connection.prepareStatement(renderedQuery.sql());
        int index = 1;
        for (Parameter parameter : renderedQuery.parameters()) {
            statement.setObject(index, parameter.value());
            index++;
        }
        return statement;
    }
}
