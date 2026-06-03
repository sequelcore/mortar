package dev.mortar.postgres;

import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.CountSpec;
import dev.mortar.core.ExistsSpec;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

final class PostgresScalarRendererTest {
    @Test
    void rendersCountWithPredicatesAndMetadata() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        CountSpec count = new SimpleMortarDb()
            .from(clients)
            .where(active.eq(true))
            .count();

        assertThatSql(new PostgresQueryRenderer().render(count))
            .hasSql("select count(*) from clients c where c.active = ?")
            .hasParameters(true)
            .hasParameterTypes(Boolean.class)
            .hasTables(clients)
            .hasColumns(active);
    }

    @Test
    void rendersExistsAsPostgresExistsSubquery() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ExistsSpec exists = new SimpleMortarDb()
            .from(clients)
            .where(id.eq(7L))
            .where(active.eq(true))
            .exists();

        assertThatSql(new PostgresQueryRenderer().render(exists))
            .hasSql("select exists (select 1 from clients c where c.id = ? and c.active = ?)")
            .hasParameters(7L, true)
            .hasParameterTypes(Long.class, Boolean.class)
            .hasTables(clients)
            .hasColumns(id, active);
    }
}
