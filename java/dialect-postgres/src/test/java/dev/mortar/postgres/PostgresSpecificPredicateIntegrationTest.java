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
import java.util.List;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresSpecificPredicateIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void executesPostgresSpecificPredicatesAgainstPostgreSQL() throws Exception {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String[]> tags = clients.column("tags", "tags", String[].class);
        ColumnRef<Object> profile = clients.column("profile", "profile", Object.class);
        ColumnRef<String> bio = clients.column("bio", "bio", String.class);
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .select(id)
                .where(PostgresPredicates.arrayContains(tags, List.of("vip")))
                .where(PostgresPredicates.arrayOverlaps(tags, List.of("beta", "trial")))
                .where(PostgresPredicates.jsonbContains(profile, "{\"status\":\"active\"}"))
                .where(PostgresPredicates.webSearch(bio, "english", "founder"))
                .build()
        );

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            createSchema(connection);
            seedClients(connection);

            try (PreparedStatement statement = connection.prepareStatement(rendered.sql())) {
                for (int index = 0; index < rendered.parameters().size(); index++) {
                    statement.setObject(index + 1, rendered.parameters().get(index).value());
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getLong("id")).isEqualTo(1L);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    @Test
    void executesUuidArrayOverlapAgainstPostgreSQL() throws Exception {
        UUID betaGroup = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID inactiveGroup = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TableRef clients = new TableRef("client_groups", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<UUID[]> groups = clients.column("groups", "groups", UUID[].class);
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .select(id)
                .where(PostgresPredicates.arrayOverlaps(groups, List.of(betaGroup)))
                .build()
        );

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("drop table if exists client_groups");
                statement.execute("create table client_groups (id bigint primary key, groups uuid[] not null)");
                statement.execute(
                    "insert into client_groups (id, groups) values "
                        + "(1, array['" + betaGroup + "'::uuid]), "
                        + "(2, array['" + inactiveGroup + "'::uuid])"
                );
            }

            try (PreparedStatement statement = connection.prepareStatement(rendered.sql())) {
                statement.setObject(1, betaGroup);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getLong("id")).isEqualTo(1L);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists clients");
            statement.execute("create table clients (id bigint primary key, tags text[] not null, profile jsonb not null, bio text not null)");
        }
    }

    private static void seedClients(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "insert into clients (id, tags, profile, bio) values "
                    + "(1, array['vip', 'beta'], '{\"status\":\"active\"}'::jsonb, 'Ada is a database founder'), "
                    + "(2, array['trial'], '{\"status\":\"inactive\"}'::jsonb, 'Grace builds compilers')"
            );
        }
    }
}
