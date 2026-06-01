package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Predicate;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PostgresQueryRendererTest {
    @Test
    void rendersTransparentPostgresSql() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .select(id, name)
                .where(name.containsIgnoreCase("rio"))
                .orderBy(name.asc())
                .limit(20)
                .offset(0)
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.id, c.name from clients c where c.name ilike ? order by c.name asc limit ? offset ?"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("%rio%", 20, 0);
    }

    @Test
    void rendersDefaultSelectionAndEqualityPredicate() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(id.eq(42L))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c where c.id = ?");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly(42L);
    }

    @Test
    void rendersExplicitInnerJoin() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .innerJoin(routes, routeId, routeTableId)
                .select(name)
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.name from clients c inner join routes r on c.route_id = r.id"
        );
        assertThat(rendered.parameters()).isEmpty();
    }

    @Test
    void rendersExplicitLeftJoin() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .leftJoin(routes, routeId, routeTableId)
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c left join routes r on c.route_id = r.id"
        );
    }

    @Test
    void rendersBooleanCompositionAndComparisonPredicates() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(Predicate.or(List.of(name.isNull(), name.containsIgnoreCase("rio"))))
                .where(id.gte(10L))
                .where(id.lt(20L))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c where (c.name is null or c.name ilike ?) and c.id >= ? and c.id < ?"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("%rio%", 10L, 20L);
    }

    @Test
    void rendersBetweenInAndNotNullPredicates() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(name.isNotNull())
                .where(id.between(1L, 10L))
                .where(id.in(List.of(2L, 4L)))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c where c.name is not null and c.id between ? and ? and c.id in (?, ?)"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly(1L, 10L, 2L, 4L);
    }

    @Test
    void attachesQueryMetadataToRenderedQuery() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .select(id)
                .where(id.eq(1L))
                .build()
        );

        assertThat(rendered.metadata().tables()).containsExactly(clients);
        assertThat(rendered.metadata().columns()).containsExactly(id);
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        TableRef clients = new TableRef("clients;drop", "c");

        assertThatThrownBy(() -> new PostgresQueryRenderer().render(new SimpleMortarDb().from(clients).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid SQL identifier: clients;drop");
    }
}
