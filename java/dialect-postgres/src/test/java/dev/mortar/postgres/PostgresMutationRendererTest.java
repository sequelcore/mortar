package dev.mortar.postgres;

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

        assertThat(rendered.sql()).isEqualTo("insert into clients (id, name) values (?, ?) returning id, name");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly(1L, "Ada");
        assertThat(rendered.metadata().columns()).containsExactly(id, name);
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

        assertThat(rendered.sql()).isEqualTo(
            "update clients set name = ? where id = ? and name is not null returning id, name"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("Ada", 1L);
    }

    @Test
    void rendersDeleteWithWhereAndReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        DeleteSpec delete = new DeleteSpec(clients, List.of(id.eq(1L)), List.of(id));

        RenderedQuery rendered = new PostgresQueryRenderer().render(delete);

        assertThat(rendered.sql()).isEqualTo("delete from clients where id = ? returning id");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly(1L);
    }
}
