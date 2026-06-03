package dev.mortar.postgres;

import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.Assignment;
import dev.mortar.core.ColumnRef;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.TableRef;
import dev.mortar.core.UpdateSpec;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PostgresMutationRendererTest {
    @Test
    void rendersInsertWithReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        InsertSpec insert = new InsertSpec(
            clients,
            List.of(Assignment.of(id, 1L), Assignment.of(name, "Ada")),
            List.of(id, name)
        );

        RenderedQuery rendered = new PostgresQueryRenderer().render(insert);

        assertThatSql(rendered)
            .hasSql("insert into clients (id, name) values (?, ?) returning id, name")
            .hasParameters(1L, "Ada")
            .hasParameterTypes(Long.class, String.class)
            .hasTables(clients)
            .hasColumns(id, name);
    }

    @Test
    void rendersUpdateWithWhereAndReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        UpdateSpec update = new UpdateSpec(
            clients,
            List.of(Assignment.of(name, "Ada")),
            List.of(id.eq(1L), name.isNotNull()),
            List.of(id, name)
        );

        RenderedQuery rendered = new PostgresQueryRenderer().render(update);

        assertThatSql(rendered)
            .hasSql("update clients set name = ? where id = ? and name is not null returning id, name")
            .hasParameters("Ada", 1L)
            .hasParameterTypes(String.class, Long.class)
            .hasTables(clients)
            .hasColumns(name, id);
    }

    @Test
    void rendersDeleteWithWhereAndReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        DeleteSpec delete = new DeleteSpec(clients, List.of(id.eq(1L)), List.of(id));

        RenderedQuery rendered = new PostgresQueryRenderer().render(delete);

        assertThatSql(rendered)
            .hasSql("delete from clients where id = ? returning id")
            .hasParameters(1L)
            .hasParameterTypes(Long.class)
            .hasTables(clients)
            .hasColumns(id);
    }
}
