package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class QueryBuilderTest {
    private final TableRef clients = new TableRef("clients", "c");
    private final TableRef routes = new TableRef("routes", "r");
    private final ColumnRef<Long> id = clients.column("id", "id", Long.class);
    private final ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
    private final ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
    private final ColumnRef<String> name = clients.column("name", "name", String.class);

    @Test
    void buildsImmutableQuerySpec() {
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .select(id, name)
            .where(name.containsIgnoreCase("rio"))
            .orderBy(name.asc())
            .limit(20)
            .offset(40)
            .build();

        assertThat(query.table()).isEqualTo(clients);
        assertThat(query.selectColumns()).containsExactly(id, name);
        assertThat(query.predicates()).hasSize(1);
        assertThat(query.sorts()).containsExactly(name.asc());
        assertThat(query.limit()).isEqualTo(20);
        assertThat(query.offset()).isEqualTo(40);
    }

    @Test
    void buildsQuerySpecWithProjection() {
        Projection projection = Projection.record(ClientRow.class, List.of(id, name));

        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .project(projection)
            .build();

        assertThat(query.projection()).contains(projection);
        assertThat(query.selectColumns()).containsExactly(id, name);
    }

    @Test
    void buildsExplicitJoinQuerySpec() {
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .innerJoin(routes, routeId, routeTableId)
            .select(id, name)
            .build();

        assertThat(query.joins()).containsExactly(
            new Join(JoinType.INNER, routes, routeId, routeTableId)
        );
    }

    @Test
    void buildsExplicitLeftJoinQuerySpec() {
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .leftJoin(routes, routeId, routeTableId)
            .build();

        assertThat(query.joins()).containsExactly(
            new Join(JoinType.LEFT, routes, routeId, routeTableId)
        );
    }

    @Test
    void rejectsInvalidPaginationValues() {
        QueryBuilder<Object> builder = new SimpleMortarDb().from(clients);

        assertThatThrownBy(() -> builder.limit(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be greater than zero");

        assertThatThrownBy(() -> builder.offset(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("offset cannot be negative");
    }

    @Test
    void validatesTableAndColumnReferences() {
        assertThatThrownBy(() -> new TableRef("", "c"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tableName cannot be blank");

        assertThatThrownBy(() -> new TableRef("clients", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias cannot be blank");

        assertThatThrownBy(() -> clients.column("", "name", String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("propertyName cannot be blank");

        assertThatThrownBy(() -> clients.column("name", "", String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("columnName cannot be blank");
    }

    @Test
    void createsPrimitiveQueryParts() {
        Predicate equality = id.eq(10L);
        Predicate composite = Predicate.and(List.of(equality));

        assertThat(equality).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(composite).isInstanceOf(Predicate.CompositePredicate.class);
        assertThat(name.desc().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    void createsBooleanPredicateComposition() {
        Predicate predicate = Predicate.or(List.of(
            name.containsIgnoreCase("rio"),
            name.containsIgnoreCase("delta")
        ));

        assertThat(predicate)
            .isEqualTo(new Predicate.CompositePredicate(
                Predicate.CompositeOperator.OR,
                List.of(name.containsIgnoreCase("rio"), name.containsIgnoreCase("delta"))
            ));
    }

    @Test
    void createsNullAndComparisonPredicates() {
        assertThat(name.isNull()).isInstanceOf(Predicate.UnaryPredicate.class);
        assertThat(name.isNotNull()).isInstanceOf(Predicate.UnaryPredicate.class);
        assertThat(id.ne(1L)).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(id.gt(1L)).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(id.gte(1L)).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(id.lt(10L)).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(id.lte(10L)).isInstanceOf(Predicate.BinaryPredicate.class);
        assertThat(id.between(1L, 10L)).isInstanceOf(Predicate.BetweenPredicate.class);
        assertThat(id.in(List.of(1L, 2L))).isInstanceOf(Predicate.InPredicate.class);
    }

    @Test
    void rejectsEmptyInPredicate() {
        assertThatThrownBy(() -> id.in(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("values cannot be empty");
    }

    private record ClientRow(Long id, String name) {
    }
}
