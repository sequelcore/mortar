package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class RelationRefTest {
    @Test
    void createsJoinFromRelationPath() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> routeId = clients.column("route", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        RelationRef relation = new RelationRef("route", routes, routeId, routeTableId);

        assertThat(relation.innerJoin()).isEqualTo(new Join(JoinType.INNER, routes, routeId, routeTableId));
        assertThat(relation.leftJoin()).isEqualTo(new Join(JoinType.LEFT, routes, routeId, routeTableId));
    }
}
