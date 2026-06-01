package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.Assignment;
import dev.mortar.core.ColumnRef;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.core.UpdateSpec;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PostgresSqlFormattingTest {
    @Test
    void defaultsToCompactSqlFormatting() {
        TableRef clients = new TableRef("clients", "c");

        RenderedQuery rendered = new PostgresQueryRenderer().render(new SimpleMortarDb().from(clients).build());

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c");
    }

    @Test
    void rendersPrettySelectSqlForLogsAndSnapshots() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer(PostgresSqlFormat.PRETTY).render(
            new SimpleMortarDb()
                .from(clients)
                .leftJoin(routes, routeId, routeTableId)
                .select(name)
                .where(name.containsIgnoreCase("ada"))
                .orderBy(name.asc())
                .limit(10)
                .offset(20)
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            """
            select c.name
            from clients c
            left join routes r on c.route_id = r.id
            where c.name ilike ?
            order by c.name asc
            limit ?
            offset ?"""
        );
    }

    @Test
    void rendersPrettyMutationSqlForLogsAndSnapshots() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer(PostgresSqlFormat.PRETTY).render(
            new UpdateSpec(
                clients,
                List.of(Assignment.of(name, "Ada")),
                List.of(id.eq(1L)),
                List.of(id, name)
            )
        );

        assertThat(rendered.sql()).isEqualTo(
            """
            update clients
            set name = ?
            where id = ?
            returning id, name"""
        );
    }
}
