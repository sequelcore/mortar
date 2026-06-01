package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;

final class QueryMetadataTest {
    @Test
    void extractsTouchedTablesColumnsAndJoins() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        Join join = new Join(JoinType.LEFT, routes, routeId, routeTableId);

        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .leftJoin(routes, routeId, routeTableId)
            .select(id, name)
            .where(Predicate.or(List.of(name.containsIgnoreCase("rio"), routeTableId.isNotNull())))
            .orderBy(name.asc())
            .build();

        QueryMetadata metadata = QueryMetadata.from(query);

        assertThat(metadata.tables()).containsExactly(clients, routes);
        assertThat(metadata.columns()).containsExactly(id, name, routeId, routeTableId);
        assertThat(metadata.joins()).containsExactly(join);
    }
}
