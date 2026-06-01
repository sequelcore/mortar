package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class MortarDbTest {
    @Test
    void startsQueryFromGeneratedTableContract() {
        ClientTable client = new ClientTable();

        QuerySpec query = new SimpleMortarDb()
            .from(client)
            .select(client.id)
            .build();

        assertThat(query.table()).isEqualTo(client.table());
        assertThat(query.selectColumns()).containsExactly(client.id);
    }

    @Test
    void supportsLambdaBasedTypedPredicatesAndSorting() {
        ClientTable client = new ClientTable();

        QuerySpec query = new SimpleMortarDb()
            .from(client)
            .where(c -> c.name.containsIgnoreCase("rio"))
            .orderBy(c -> c.name.asc())
            .build();

        assertThat(query.predicates()).containsExactly(client.name.containsIgnoreCase("rio"));
        assertThat(query.sorts()).containsExactly(client.name.asc());
    }

    @Test
    void supportsTypedSelectionAndPagination() {
        ClientTable client = new ClientTable();

        QuerySpec query = new SimpleMortarDb()
            .from(client)
            .select(c -> c.id, c -> c.name)
            .page(MortarPage.of(2, 25))
            .build();

        assertThat(query.selectColumns()).containsExactly(client.id, client.name);
        assertThat(query.limit()).isEqualTo(25);
        assertThat(query.offset()).isEqualTo(50);
    }

    @Test
    void supportsRecordAndDtoProjectionApi() {
        ClientTable client = new ClientTable();

        QuerySpec recordQuery = new SimpleMortarDb()
            .from(client)
            .projectRecord(ClientRow.class, c -> c.id, c -> c.name)
            .build();

        QuerySpec dtoQuery = new SimpleMortarDb()
            .from(client)
            .projectDto(ClientDto.class, c -> c.id, c -> c.name)
            .build();

        assertThat(recordQuery.projection())
            .hasValue(Projection.record(ClientRow.class, java.util.List.of(client.id, client.name)));
        assertThat(dtoQuery.projection())
            .hasValue(Projection.dto(ClientDto.class, java.util.List.of(client.id, client.name)));
    }

    @Test
    void supportsQueryNameForDebugOutput() {
        ClientTable client = new ClientTable();

        QuerySpec query = new SimpleMortarDb()
            .from(client)
            .named("ClientSearchRepository.search")
            .build();

        assertThat(query.name()).contains("ClientSearchRepository.search");
    }

    @Test
    void supportsJoinThroughGeneratedRelationPath() {
        ClientTable client = new ClientTable();

        QuerySpec query = new SimpleMortarDb()
            .from(client)
            .innerJoin(c -> c.route)
            .build();

        assertThat(query.joins()).containsExactly(client.route.innerJoin());
    }

    private static final class ClientTable implements MortarTable {
        private final TableRef table = new TableRef("clients", "c");
        private final TableRef routeTable = new TableRef("routes", "r");
        private final ColumnRef<Long> id = table.column("id", "id", Long.class);
        private final ColumnRef<String> name = table.column("name", "name", String.class);
        private final RelationRef route = new RelationRef(
            "route",
            routeTable,
            table.column("route", "route_id", Long.class),
            routeTable.column("id", "id", Long.class)
        );

        @Override
        public TableRef table() {
            return table;
        }
    }

    private record ClientRow(Long id, String name) {
    }

    private static final class ClientDto {
    }
}
