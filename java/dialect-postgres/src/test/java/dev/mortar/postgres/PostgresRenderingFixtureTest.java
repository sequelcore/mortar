package dev.mortar.postgres;

import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Parameter;
import dev.mortar.core.Predicate;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class PostgresRenderingFixtureTest {
    @TestFactory
    Stream<DynamicTest> rendersEdgeCaseFixtures() {
        return fixtures().stream()
            .map(fixture -> dynamicTest(fixture.name(), () -> {
                RenderedQuery renderedQuery = fixture.renderedQuery().get();

                assertThatSql(renderedQuery)
                    .hasSql(fixture.expectedSql())
                    .hasParameters(fixture.expectedParameters());
            }));
    }

    private static List<RenderingFixture> fixtures() {
        return List.of(
            nestedBooleanFixture(),
            unsafeRawPredicateFixture(),
            prettyJoinPaginationFixture()
        );
    }

    private static RenderingFixture nestedBooleanFixture() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        return new RenderingFixture(
            "nested boolean predicate preserves grouping",
            () -> new PostgresQueryRenderer().render(
                new SimpleMortarDb()
                    .from(clients)
                    .where(Predicate.or(List.of(
                        name.isNull(),
                        Predicate.and(List.of(id.gte(10L), id.lte(20L)))
                    )))
                    .build()
            ),
            "select c.* from clients c where (c.name is null or (c.id >= ? and c.id <= ?))",
            new Object[] {10L, 20L}
        );
    }

    private static RenderingFixture unsafeRawPredicateFixture() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<String> name = clients.column("name", "name", String.class);

        return new RenderingFixture(
            "unsafe raw predicate keeps explicit parameter order",
            () -> new PostgresQueryRenderer().render(
                new SimpleMortarDb()
                    .from(clients)
                    .where(name.containsIgnoreCase("ada"))
                    .unsafeWhereRaw("c.score > ?::numeric", Parameter.of(90))
                    .build()
            ),
            "select c.* from clients c where c.name ilike ? and c.score > ?::numeric",
            new Object[] {"%ada%", 90}
        );
    }

    private static RenderingFixture prettyJoinPaginationFixture() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        return new RenderingFixture(
            "pretty join pagination remains snapshot stable",
            () -> new PostgresQueryRenderer(PostgresSqlFormat.PRETTY).render(
                new SimpleMortarDb()
                    .from(clients)
                    .leftJoin(routes, routeId, routeTableId)
                    .select(name)
                    .where(name.isNotNull())
                    .orderBy(name.asc())
                    .limit(25)
                    .offset(50)
                    .build()
            ),
            """
            select c.name
            from clients c
            left join routes r on c.route_id = r.id
            where c.name is not null
            order by c.name asc
            limit ?
            offset ?""",
            new Object[] {25, 50}
        );
    }

    private record RenderingFixture(
        String name,
        Supplier<RenderedQuery> renderedQuery,
        String expectedSql,
        Object[] expectedParameters
    ) {
    }
}
