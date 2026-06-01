package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

final class QueryDiagnosticsTest {
    @Test
    void warnsWhenCollectionQueryHasNoPagination() {
        TableRef clients = new TableRef("clients", "c");
        QuerySpec query = new SimpleMortarDb().from(clients).build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.UNBOUNDED_QUERY);
    }

    @Test
    void warnsWhenQueryUsesDefaultSelectAll() {
        TableRef clients = new TableRef("clients", "c");
        QuerySpec query = new SimpleMortarDb().from(clients).limit(10).build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.SELECT_ALL);
    }

    @Test
    void warnsWhenPaginatedQueryHasNoStableOrdering() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        QuerySpec query = new SimpleMortarDb().from(clients).select(id).limit(10).build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.UNSTABLE_PAGINATION);
    }

    @Test
    void warnsWhenInListExceedsStaticSafetyLimit() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .select(id)
            .where(id.in(LongStream.rangeClosed(1, 101).boxed().toList()))
            .orderBy(id.asc())
            .limit(10)
            .build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.LARGE_IN_LIST);
    }

    @Test
    void doesNotWarnForBoundedSelectedOrderedQueryWithSmallInList() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .select(id)
            .where(id.in(List.of(1L, 2L)))
            .orderBy(id.asc())
            .limit(10)
            .build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .filteredOn(diagnostic -> diagnostic.severity() == MortarDiagnosticSeverity.WARNING)
            .isEmpty();
    }

    @Test
    void warnsWhenNullableRelationshipUsesInnerJoin() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> clientRouteId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeId = routes.column("id", "id", Long.class);
        ColumnRef<Long> clientId = clients.column("id", "id", Long.class);
        QuerySpec query = new QuerySpec(
            java.util.Optional.empty(),
            clients,
            List.of(clientId),
            java.util.Optional.empty(),
            List.of(new Join(JoinType.INNER, routes, clientRouteId, routeId, true)),
            List.of(),
            List.of(clientId.asc()),
            10,
            null
        );

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.NULLABLE_RELATION_INNER_JOIN);
    }

    @Test
    void warnsWhenRenderedQueryPatternRepeatsOften() {
        List<RenderedQuery> renderedQueries = Stream.generate(() -> new RenderedQuery("select c.* from clients c where c.id = ?", List.of(Parameter.of(7L))))
            .limit(11)
            .toList();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyzeRenderedQueries(renderedQueries);

        assertThat(diagnostics)
            .extracting(MortarDiagnostic::code)
            .contains(MortarDiagnosticCode.REPEATED_QUERY_PATTERN);
    }

    @Test
    void doesNotWarnForSmallRenderedQueryRepetition() {
        List<RenderedQuery> renderedQueries = Stream.generate(() -> new RenderedQuery("select c.* from clients c where c.id = ?", List.of(Parameter.of(7L))))
            .limit(2)
            .toList();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyzeRenderedQueries(renderedQueries);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void addsIndexAdvisoryForFilterJoinAndSortColumns() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> clientId = clients.column("id", "id", Long.class);
        ColumnRef<Long> clientRouteId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeId = routes.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .leftJoin(routes, clientRouteId, routeId)
            .select(clientId)
            .where(name.eq("Ada"))
            .orderBy(name.asc())
            .limit(10)
            .build();

        List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);

        assertThat(diagnostics)
            .filteredOn(diagnostic -> diagnostic.code() == MortarDiagnosticCode.INDEX_ADVISORY)
            .singleElement()
            .satisfies(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(MortarDiagnosticSeverity.INFO);
                assertThat(diagnostic.message())
                    .contains("clients.route_id")
                    .contains("routes.id")
                    .contains("clients.name");
            });
    }
}
